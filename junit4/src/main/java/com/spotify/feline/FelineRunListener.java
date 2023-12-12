/*-
 * -\-\-
 * futuristic-feline-junit4
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

import java.lang.reflect.Method;
import org.junit.runner.notification.RunListener;

public class FelineRunListener extends RunListener {

  static {
    // Install when JUnit Runner loads this class
    Feline.addConsumerLast(
        blockingCall -> {
          boolean prevBlocking = false;
          boolean ignore = false;
          for (final StackTraceElement line : Thread.currentThread().getStackTrace()) {

            if (line.getClassName().equals("java.util.concurrent.CompletableFuture")
                && (line.getMethodName().equals("get") || line.getMethodName().equals("join"))) {
              prevBlocking = true;
            } else {
              if (prevBlocking) {
                try {
                  final Method method = BytecodeUtils.getMethod(line);
                  if (method.getAnnotation(IgnoreBlocking.class) != null) {
                    ignore = true;
                    break;
                  }
                } catch (Exception ignored) {
                  // exception ignored
                }
              }
              prevBlocking = false;
            }
          }

          if (!ignore) {
            throw new RuntimeException("blocking call at " + blockingCall);
          }
        });
  }
}
