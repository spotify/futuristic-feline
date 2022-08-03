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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * This transformer applies {@link AllowAdvice} to every method registered with {@link
 * Feline#allowBlockingCallsInside(String, String)}.
 */
class AllowancesTransformer implements AgentBuilder.Transformer {

  private final ConcurrentMap<String, Set<String>> allowances;

  AllowancesTransformer() {
    this.allowances = new ConcurrentHashMap<>();
  }

  void allow(final String className, final String methodName) {
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

  boolean containsClass(String name) {
    return allowances.containsKey(name);
  }

  @Override
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module) {

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
