/*-
 * -\-\-
 * futuristic-feline-semantic-metrics
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.SettableFuture;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricRegistry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Test;

public class MetricsConsumerTest {

  private final MetricId BLOCKING_CALL_ID = MetricId.EMPTY.tagged("what", "blocking-calls");

  private static final Runnable RUNNABLE =
      () -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      };

  private final SemanticMetricRegistry registry = new SemanticMetricRegistry();

  @After
  public void tearDown() {
    // remove any consumers added by tests below
    FelineRuntime.clearConsumers();
  }

  @Test
  public void blockingJoin() {
    Feline.addConsumerLast(new MetricsConsumer(registry));

    // blocking
    CompletableFuture.runAsync(RUNNABLE).join();

    assertMetric("com.spotify.feline.MetricsConsumerTest.blockingJoin");
  }

  @Test
  public void blockingGet() throws ExecutionException, InterruptedException {
    Feline.addConsumerLast(new MetricsConsumer(registry));

    // blocking
    CompletableFuture.runAsync(RUNNABLE).get();

    assertMetric("com.spotify.feline.MetricsConsumerTest.blockingGet");
  }

  @Test
  public void multipleBlockingGets() throws ExecutionException, InterruptedException {
    Feline.addConsumerLast(new MetricsConsumer(registry));

    blockfun1();
    blockfun2();

    assertMetric("com.spotify.feline.MetricsConsumerTest.blockfun1");
    assertMetric("com.spotify.feline.MetricsConsumerTest.blockfun2");
  }

  private void blockfun1() throws InterruptedException, ExecutionException {
    CompletableFuture.runAsync(RUNNABLE).get();
  }

  private void blockfun2() throws InterruptedException, ExecutionException {
    CompletableFuture.runAsync(RUNNABLE).get();
  }

  @Test
  public void blockingGetTimeout()
      throws ExecutionException, InterruptedException, TimeoutException {
    Feline.addConsumerLast(new MetricsConsumer(registry));

    // blocking
    CompletableFuture.runAsync(RUNNABLE).get(10, TimeUnit.SECONDS);

    assertMetric("com.spotify.feline.MetricsConsumerTest.blockingGetTimeout");
  }

  @Test
  public void blockingListenableFutureGet() throws Exception {
    Feline.addConsumerLast(new MetricsConsumer(registry));

    SettableFuture<Integer> lf = SettableFuture.create();

    CompletableFuture.runAsync(RUNNABLE).thenAccept(ignored -> lf.set(123));

    lf.get();

    assertMetric("com.spotify.feline.MetricsConsumerTest.blockingListenableFutureGet");
  }

  @Test
  public void blockingListenableFutureGetTimeout() throws Exception {
    Feline.addConsumerLast(new MetricsConsumer(registry));

    SettableFuture<Integer> lf = SettableFuture.create();

    CompletableFuture.runAsync(RUNNABLE).thenAccept(ignored -> lf.set(123));

    lf.get(1, TimeUnit.SECONDS);

    assertMetric("com.spotify.feline.MetricsConsumerTest.blockingListenableFutureGetTimeout");
  }

  private void assertMetric(final String call) {
    final String expectedThreadName =
        MetricsConsumer.sanitizeThreadName(Thread.currentThread().getName());
    final MetricId expectedId =
        BLOCKING_CALL_ID.tagged("call", call, "thread_name", expectedThreadName);
    assertTrue(
        "Did not find meter with id="
            + expectedId
            + ", all meters in registry: "
            + registry.getMeters().keySet(),
        registry.getMeters().containsKey(expectedId));
  }

  @Test
  public void testCallFinderFindsNothing() {
    Feline.addConsumerLast(
        new MetricsConsumer(registry, (elements, indexOfBlockingCall) -> Optional.empty()));

    // blocking
    CompletableFuture.runAsync(RUNNABLE).join();

    // predicate does not test true for any element, no metric should be recorded
    assertTrue(registry.getMeters().isEmpty());
  }

  @Test
  public void testDefaultCallFinder() {
    MetricsConsumer.DefaultCallFinder finder = new MetricsConsumer.DefaultCallFinder();

    StackTraceElement[] elements =
        new StackTraceElement[] {
          new StackTraceElement("java.util.concurrent.Future", "get", "Future.java", 999),
          new StackTraceElement("java.something.Something", "get", "Something.java", 123),
          new StackTraceElement("com.foobar.Library", "getFooBar", "Library.java", 456),
          new StackTraceElement(
              "com.spotify.FoobarUsingClass", "blah", "FoobarUsingClass.java", 789),
          new StackTraceElement("com.spotify.service.Main", "main", "Main.java", 12),
          new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1),
        };

    // StackTraceElement doesn't override equals() so do an instance check
    Optional<StackTraceElement> stackTraceElement =
        finder.findCall(elements, "java.util.concurrent.Future.get");
    assertTrue(stackTraceElement.isPresent());
    assertThat(stackTraceElement.get(), is(sameInstance(elements[3])));
  }

  @Test
  public void testDefaultCallFinder_NoSpotifyFrames() {
    MetricsConsumer.DefaultCallFinder finder = new MetricsConsumer.DefaultCallFinder();

    StackTraceElement[] elements =
        new StackTraceElement[] {
          new StackTraceElement("java.util.concurrent.Future", "get", "Future.java", 999),
          new StackTraceElement("java.something.Something", "get", "Something.java", 123),
          new StackTraceElement("com.foobar.Library", "getFooBar", "Library.java", 456),
          new StackTraceElement("java.lang.Thread", "run", "Thread.java", 1),
        };

    Optional<StackTraceElement> stackTraceElement =
        finder.findCall(elements, "java.util.concurrent.Future.get");
    assertTrue(stackTraceElement.isPresent());
    assertThat(stackTraceElement.get(), is(sameInstance(elements[2])));
  }

  @Test
  public void testSanitizeThread() {
    assertEquals("thread-N-N", MetricsConsumer.sanitizeThreadName("thread-2-10"));
    assertEquals("thread-N-N-pool", MetricsConsumer.sanitizeThreadName("thread-256-123-pool"));
  }
}
