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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FelineStateFutureThrowsTest {

  private static final Runnable RUNNABLE =
      () -> {
        try {
          Thread.sleep(100);
          throw new RuntimeException();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };

  private static final BlockingQueue<String> blockedCalls = new ArrayBlockingQueue<>(100);

  @BeforeAll
  public static void classSetUp() {
    Feline.addConsumerLast(blockedCalls::add);
  }

  @Test
  public void blockingJoin() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // will throw exception
    assertThrows(RuntimeException.class, future::join);

    // this makes sure the first test does not screw up the FelineRuntime state
    final CompletableFuture<Void> future2 = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    assertThrows(RuntimeException.class, future2::join);

    // must detect two blocked calls as to make sure state is correctly reset when exceptions are
    // thrown by the future
    assertCalls(
        "java.util.concurrent.CompletableFuture.join()",
        "java.util.concurrent.CompletableFuture.join()");
  }

  private static void assertCalls(String... expected) {
    List<String> actual = new ArrayList<>();
    blockedCalls.drainTo(actual);
    assertEquals(Arrays.asList(expected), actual);
  }
}
