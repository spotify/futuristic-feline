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

import static org.junit.Assert.assertThrows;

import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public abstract class JUnit4TestTemplate {

  private static final Runnable RUNNABLE =
      () -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };

  @Test
  public void blockingJoin() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    assertThrows(RuntimeException.class, future::join);
  }

  @Test
  @IgnoreBlocking
  public void ignoreBlockingJoin() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // blocking, should work since we're ignoring blocking calls
    future.join();
  }

  @Test
  @IgnoreBlocking
  public void indirectIgnoreBlockingJoin() {
    assertThrows(RuntimeException.class, this::blockingCall);
  }

  private void blockingCall() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    future.join();
  }

  @Test
  public void indirectIgnoreBlockingJoin2() {
    blockingCall2();
  }

  @IgnoreBlocking
  private void blockingCall2() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    future.join();
  }
}
