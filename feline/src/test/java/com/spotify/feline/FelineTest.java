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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FelineTest {

  private static final Runnable RUNNABLE =
      () -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };

  private static final BlockingQueue<String> blockedCalls = new ArrayBlockingQueue<>(100);

  @BeforeAll
  public static void classSetUp() {
    Feline.addConsumerFirst(System.out::println);

    Feline.addConsumerLast(blockedCalls::add);

    Feline.allowBlockingCallsInside("com.spotify.feline.AllowanceTester", "allowed");
    Feline.allowBlockingCallsInside("com.spotify.feline.AllowanceTester", "nested");
  }

  @Test
  public void consumerOrdering() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    final StringBuilder sb = new StringBuilder();
    final Consumer<String> a = s -> sb.append("a");
    final Consumer<String> b = s -> sb.append("b");
    final Consumer<String> c = s -> sb.append("c");
    final Consumer<String> d = s -> sb.append("d");
    Feline.addConsumerFirst(a);
    Feline.addConsumerFirst(b);
    Feline.addConsumerLast(c);
    Feline.addConsumerLast(d);

    // blocking
    future.join();

    // non-blocking
    future.get();

    assertCalls("java.util.concurrent.CompletableFuture.join()");

    assertEquals("bacd", sb.toString());

    assertTrue(FelineRuntime.removeConsumer(a));
    assertTrue(FelineRuntime.removeConsumer(b));
    assertTrue(FelineRuntime.removeConsumer(c));
    assertTrue(FelineRuntime.removeConsumer(d));
  }

  @Test
  public void completableFutureBlockingJoin() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    future.join();

    // non-blocking
    future.get();

    assertCalls("java.util.concurrent.CompletableFuture.join()");
  }

  @Test
  public void completableFutureBlockingJoinWithSubclass()
      throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);
    final FutureSubclass<Void> subFuture = new FutureSubclass<>();
    future.whenComplete(
        (aVoid, throwable) -> {
          if (throwable != null) {
            subFuture.completeExceptionally(throwable);
          } else {
            subFuture.complete(aVoid);
          }
        });

    // blocking
    subFuture.join();

    // non-blocking
    subFuture.get();

    assertCalls("java.util.concurrent.CompletableFuture.join()");
  }

  @Test
  public void completableFutureAllowedBlockingJoin() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // AllowanceTester is allow listed above
    new AllowanceTester().allowed(future);

    assertCalls();
  }

  @Test
  public void completableFutureAllowedNestedBlockingJoin() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // AllowanceTester is allow listed above
    new AllowanceTester().nested(future);

    assertCalls();
  }

  @Test
  public void completableFutureBlockingGet() throws ExecutionException, InterruptedException {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    future.get();

    // non-blocking
    future.join();

    assertCalls("java.util.concurrent.CompletableFuture.get()");
  }

  @Test
  public void completableFutureBlockingGetWithTimeout()
      throws ExecutionException, InterruptedException, TimeoutException {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    // blocking
    future.get(10, TimeUnit.SECONDS);

    // non-blocking
    future.join();

    assertCalls("java.util.concurrent.CompletableFuture.get(long,java.util.concurrent.TimeUnit)");
  }

  @Test
  public void completableFutureNonBlockingGet() {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(RUNNABLE);

    final CompletableFuture<Void> future2 =
        future.thenApply(
            aVoid -> {
              try {
                // non-blocking
                return future.get();
              } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
              }
            });

    // blocking
    future2.join();

    // the join should show up here, but the non-blocking get must not
    assertCalls("java.util.concurrent.CompletableFuture.join()");
  }

  /*
   * Tests that blocking get() calls on a Guava ListenableFuture subclass are intercepted, as a
   * proxy for testing that any subtype of Future can be intercepted.
   */

  @Test
  public void futureNonBlockingGet() throws Exception {
    SettableFuture<Void> future = SettableFuture.create();
    future.set(null);

    // should not block
    future.get();

    assertCalls();
  }

  @Test
  public void futureBlockingGet() throws Exception {
    SettableFuture<Void> future = SettableFuture.create();

    // on another thread: set the future to complete after sleeping for an interval
    CompletableFuture.runAsync(RUNNABLE).thenAccept(ignored -> future.set(null));

    // should block
    future.get();

    // should not block
    future.get();

    assertCalls("com.google.common.util.concurrent.AbstractFuture$TrustedFuture.get()");
  }

  @Test
  public void futureBlockingGetWithTimeout() throws Exception {
    SettableFuture<Void> future = SettableFuture.create();

    // on another thread: set the future to complete after sleeping for an interval
    CompletableFuture.runAsync(RUNNABLE).thenAccept(ignored -> future.set(null));

    // should block
    future.get(1, TimeUnit.SECONDS);

    // should not block
    future.get(1, TimeUnit.SECONDS);

    assertCalls(
        "com.google.common.util.concurrent.AbstractFuture$TrustedFuture.get(long,java.util.concurrent.TimeUnit)");
  }

  private static void assertCalls(String... expected) {
    List<String> actual = new ArrayList<>();
    blockedCalls.drainTo(actual);
    assertEquals(Arrays.asList(expected), actual);
  }

  @AfterEach
  public void tearDown() {
    blockedCalls.clear();
  }
}
