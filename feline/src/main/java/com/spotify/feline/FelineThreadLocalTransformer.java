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
 * Copyright (c) 2022 Spotify AB
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
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

class FelineThreadLocalTransformer implements AgentBuilder.Transformer {

  private final ElementMatcher.Junction<NamedElement> matcher;

  public static AgentBuilder.Transformer forThreadLocal() {
    return new FelineThreadLocalTransformer(named("initialValue"));
  }

  private FelineThreadLocalTransformer(final ElementMatcher.Junction<NamedElement> matcher) {
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
    static void onEnter() {
      FelineRuntime.acceptThreadLocalInitialValue();
    }
  }
}
