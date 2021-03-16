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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that a throwable thrown by a consumer are bubbled up to the code calling the blocking
 * Future method.
 */
public class FelineStateConsumerThrowsTest {

  private static final Runnable RUNNABLE =
      () -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };

  /**
   * A custom RuntimeException so we can test that the same type is caught by calling code below.
   */
  private static class CustomThrownException extends RuntimeException {

    public CustomThrownException(String message) {
      super(message);
    }
  }

  private static final Consumer<String> throwingConsumer =
      s -> {
        throw new CustomThrownException(s);
      };

  @BeforeAll
  public static void classSetUp() {
    Feline.addConsumerLast(throwingConsumer);
  }

  @AfterAll
  public static void classTearDown() {
    Feline.removeConsumer(throwingConsumer);
  }

  @Test
  public void blockingJoin() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    assertThrows(CustomThrownException.class, future::join);

    // blocking, must detect two blocked calls as to make sure state is correctly reset when
    // exceptions are thrown by
    // the consumer
    final CompletableFuture<Void> future2 = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    assertThrows(CustomThrownException.class, future2::join);
  }
}
