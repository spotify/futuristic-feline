/*
 * Copyright (c) 2021 Spotify AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.spotify.feline;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.matcher.ElementMatchers;

/** Detects blocking calls to @link CompletableFuture and notifies registered consumers. */
public class Feline {

  private static final Map<String, Set<String>> allowances = new ConcurrentHashMap<>();

  /**
   * Registers a consumer that will be invoked when blocking calls are detected. Consumers can throw
   * exceptions and can thus affect the blocking call.
   *
   * <p>This adds the consumer to the front of the list of consumers, so it will be invoked before
   * other consumers (until something else also calls this). Typically you want to use this for
   * consumers that only want to observe the state and be guaranteed to be called.
   *
   * <p>Consumers are called on the blocked thread.
   *
   * <p>If a consumer throws an exception, subsequent consumers will not be invoked.
   *
   * @param blockingCallConsumer Consumer to be invoked on any blocking call
   */
  public static void addConsumerFirst(final Consumer<String> blockingCallConsumer) {
    FelineRuntime.addConsumerFirst(blockingCallConsumer);
  }

  /**
   * Registers a consumer that will be invoked when blocking calls are detected. Consumers can throw
   * exceptions and can thus affect the blocking call.
   *
   * <p>This adds the consumer to the end of the list of consumers, so it will be invoked after
   * other consumers (until something else also calls this). Typically you want to use this for
   * consumers that only want to affect the state by throwing an exception.
   *
   * <p>Consumers are called on the blocked thread.
   *
   * <p>If a consumer throws an exception, subsequent consumers will not be invoked.
   *
   * @param blockingCallConsumer Consumer to be invoked on any blocking call
   */
  public static void addConsumerLast(final Consumer<String> blockingCallConsumer) {
    FelineRuntime.addConsumerLast(blockingCallConsumer);
  }

  /**
   * Removes a consumer from the internal list of consumers.
   *
   * @param blockingCallConsumer Consumer to be invoked on any blocking call
   * @return true if it was found, false if it was not found.
   */
  public static boolean removeConsumer(final Consumer<String> blockingCallConsumer) {
    return FelineRuntime.removeConsumer(blockingCallConsumer);
  }

  /**
   * Allows blocking calls inside any method of a class with name identified by the provided
   * className and which name matches the provided methodName.
   *
   * @param className class' name
   * @param methodName a method name
   */
  public static void allowBlockingCallsInside(final String className, final String methodName) {
    allowances.compute(
        className,
        (key, allowances) -> {
          if (allowances == null) {
            allowances = new HashSet<>();
          }
          allowances.add(methodName);
          return allowances;
        });
  }

  static {
    final Instrumentation instrumentation = ByteBuddyAgent.install();

    try {
      BytecodeUtils.injectBootstrapClasses(
          instrumentation, Feline.class.getName().replace('.', '/') + "Runtime");
    } catch (IOException e) {
      throw new RuntimeException("Failed to inject Feline runtime", e);
    }

    new AgentBuilder.Default()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .with(AgentBuilder.TypeStrategy.Default.DECORATE)
        .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
        // note: for debugging, it may help to remove .withErrorsOnly()
        .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())

        // Do not ignore JDK classes
        .ignore(ElementMatchers.nameStartsWith("com.intellij.rt."))

        // instrument CompletableFuture
        .type(ElementMatchers.is(CompletableFuture.class))
        .transform(FelineTransformer.forCompletableFuture())
        .asTerminalTransformation()

        // instrument all subtypes of Future, except for CompletableFuture (as the above
        // transformation is terminal)
        .type(ElementMatchers.isSubTypeOf(Future.class))
        .transform(FelineTransformer.forFuture())
        .asTerminalTransformation()

        // Instrument allowed/disallowed methods
        .type(it -> allowances.containsKey(it.getName()))
        .transform(new AllowancesTransformer(allowances))
        .asTerminalTransformation()
        .installOn(instrumentation);
  }
}
