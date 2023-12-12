/*-
 * -\-\-
 * futuristic-feline
 * --
 * Copyright (C) 2016 - 2023 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

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

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

class FelineTransformer implements AgentBuilder.Transformer {

  private final ElementMatcher.Junction<NamedElement> matcher;

  public static FelineTransformer forCompletableFuture() {
    return new FelineTransformer(named("get").or(named("join")));
  }

  public static AgentBuilder.Transformer forFuture() {
    return new FelineTransformer(named("get"));
  }

  private FelineTransformer(final ElementMatcher.Junction<NamedElement> matcher) {
    this.matcher = matcher;
  }

  @Override
  public Builder<?> transform(
      final Builder<?> builder,
      final TypeDescription typeDescription,
      final ClassLoader classLoader,
      final JavaModule javaModule,
      final ProtectionDomain protectionDomain) {
    return builder.visit(Advice.to(FutureCallAdvice.class).on(matcher));
  }

  static class FutureCallAdvice {

    @Advice.OnMethodEnter
    static Map<String, Object> onEnter(
        @Advice.This() final Object thisObject,
        @Advice.Origin("#t") final String typeName,
        @Advice.Origin("#m") final String methodName,
        @Advice.Origin("#s") final String methodSig) {
      final Future<?> future = (Future<?>) thisObject;

      boolean state = FelineRuntime.BLOCKED_THREADS.contains(Thread.currentThread().getId());

      if (state) {
        // Already inside a blocking operation, fast-exit
        // and keep state as true (i.e. do nothing)
        return Collections.emptyMap();
      }

      if (future.isDone()) {
        // This method call is not actually blocking, so fast-exit
        // and keep state as false (i.e. do nothing)
        return Collections.emptyMap();
      }

      final String methodCall = typeName + "." + methodName + methodSig;
      FelineRuntime.accept(methodCall);

      // Set state to true as to ignore any nested blocked calls, e.g. where one Future delegates
      // to another.
      // The state must be set after consumers are invoked above. Consumers can throw exceptions,
      // in which case we
      // must not have modified the state first as it would not be reset in onExit().
      FelineRuntime.BLOCKED_THREADS.add(Thread.currentThread().getId());

      // Can't use custom classes here, since they won't be visible to classes in standard library
      // such as Future.

      final Map<String, Object> data = new HashMap<>(2);
      data.put("method", methodCall);
      data.put("startTimeNanos", System.nanoTime());

      return data;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit(@Advice.Enter Map<String, Object> data) {
      final Object startTimeNanosObj = data.remove("startTimeNanos");
      if (startTimeNanosObj != null) {
        FelineRuntime.BLOCKED_THREADS.remove(Thread.currentThread().getId());
        final long startTimeNanos = (Long) startTimeNanosObj;
        final long endTimeNanos = System.nanoTime();
        data.put("blockedTimeNanos", endTimeNanos - startTimeNanos);
        FelineRuntime.acceptOnExit(data);
      }
    }
  }
}
