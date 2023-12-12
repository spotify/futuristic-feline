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
 * Copyright (c) 2019-Present Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spotify.feline;

import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

/**
 * This transformer applies {@link AllowAdvice} to every method registered with {@link
 * Feline#allowBlockingCallsInside(String, String)}.
 */
class AllowancesTransformer implements AgentBuilder.Transformer, ElementMatcher<TypeDescription> {

  private final ConcurrentMap<String, Set<String>> allowances;

  AllowancesTransformer() {
    this.allowances = new ConcurrentHashMap<>();
  }

  void allow(final String className, final String methodName) {
    allowances.compute(
        className,
        (key, set) -> {
          if (set == null) {
            set = new HashSet<>();
          }
          set.add(methodName);
          return set;
        });
  }

  @Override
  public boolean matches(final TypeDescription typeDescription) {
    return allowances.containsKey(typeDescription.getName());
  }

  @Override
  public Builder<?> transform(
      final Builder<?> builder,
      final TypeDescription typeDescription,
      final ClassLoader classLoader,
      final JavaModule javaModule,
      final ProtectionDomain protectionDomain) {

    final Set<String> methods = allowances.get(typeDescription.getName());

    return builder.visit(
        Advice.to(AllowAdvice.class).on(method -> methods.contains(method.getInternalName())));
  }

  static class AllowAdvice {

    @Advice.OnMethodEnter
    static void onEnter() {
      FelineRuntime.BLOCKED_THREADS.add(Thread.currentThread().getId());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit() {
      FelineRuntime.BLOCKED_THREADS.remove(Thread.currentThread().getId());
    }
  }
}
