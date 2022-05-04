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
   * @param blockingCallConsumer Consumer to be invoked on any blocking call before it starts to
   *     block.
   */
  public static void addConsumerFirst(final Consumer<String> blockingCallConsumer) {
    FelineRuntime.addConsumerFirst(blockingCallConsumer);
  }

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
   * <p>This is similar to {@link #addConsumerFirst} except the callback is called after the
   * blocking call completes, and the callback format is different.
   *
   * <p>The consumer receives a map with the following fields: "method" (a String representing the
   * blocking method call), "blockedTimeNanos" (a Long representing how long the method call was
   * blocked, in nanoseconds)
   *
   * @param blockingCallConsumer Consumer to be invoked on any blocking call after it completes.
   */
  public static void addOnExitConsumerFirst(
      final Consumer<Map<String, Object>> blockingCallConsumer) {
    FelineRuntime.addOnExitConsumerFirst(blockingCallConsumer);
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
   * <p>This is similar to {@link #addConsumerFirst} except the callback is called after the
   * blocking call completes, and the callback format is different.
   *
   * <p>The consumer receives a map with the following fields: "method" (a String representing the
   * blocking method call), "blockedTimeNanos" (a Long representing how long the method call was
   * blocked, in nanoseconds)
   *
   * @param blockingCallConsumer Consumer to be invoked on any blocking call after it completes.
   */
  public static void addOnExitConsumerLast(
      final Consumer<Map<String, Object>> blockingCallConsumer) {
    FelineRuntime.addOnExitConsumerLast(blockingCallConsumer);
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
   * Removes a consumer from the internal list of consumers.
   *
   * @param blockingCallConsumer Consumer to be invoked on any blocking call
   * @return true if it was found, false if it was not found.
   */
  public static boolean removeOnExitConsumer(
      final Consumer<Map<String, Object>> blockingCallConsumer) {
    return FelineRuntime.removeOnExitConsumer(blockingCallConsumer);
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

  /**
   * The consumer will be called every time a ThreadLocal object triggers initialValue(). This
   * should be a rare event for well behaving usages of ThreadLocal.
   *
   * <p>However, since Java 17+, the combination of ThreadLocal and ForkJoinPool.commonPool() may
   * lead to overly frequent calls to initialValue() which can be harmful for performance and/or
   * correctness. This can be used to detect suspicious high rate of calls.
   *
   * @param consumer consumer to be invoked on each call to ThreadLocal.initialValue()
   */
  public static void addThreadLocalInitialValueConsumer(final Runnable consumer) {
    FelineRuntime.addThreadLocalInitialValueConsumer(consumer);
  }

  public static boolean removeThreadLocalInitialValueConsumer(final Runnable consumer) {
    return FelineRuntime.removeThreadLocalInitialValueConsumer(consumer);
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
        .with(new ThreadLocalCircularityLock())
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

        // instrument ThreadLocal
        .type(ElementMatchers.isSubTypeOf(ThreadLocal.class))
        .transform(FelineThreadLocalTransformer.forThreadLocal())
        .asTerminalTransformation()
        .installOn(instrumentation);
  }

  /**
   * This is equivalent to AgentBuilder.CircularityLock.Default except it avoids using ThreadLocal.
   *
   * <p>We can not use ThreadLocal since we are also instrumenting it.
   */
  private static class ThreadLocalCircularityLock implements AgentBuilder.CircularityLock {
    private final Set<Long> threadLocal = ConcurrentHashMap.newKeySet();

    @Override
    public boolean acquire() {
      return threadLocal.add(Thread.currentThread().getId());
    }

    @Override
    public void release() {
      threadLocal.remove(Thread.currentThread().getId());
    }
  }
}
