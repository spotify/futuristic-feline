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

import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.SettableFuture;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Test;

public class FelineMetricsRecorderTest {

  private final MetricId BLOCKING_CALL_ID = MetricId.EMPTY.tagged("what", "blocking-calls");
  private final MetricId BLOCKING_TIME_CALL_ID =
      MetricId.EMPTY.tagged("what", "blocking-calls-time", "unit", "ns");

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
    FelineMetricsRecorder.install(registry);

    // blocking
    CompletableFuture.runAsync(RUNNABLE).join();

    assertMetric(getClass().getCanonicalName() + ".blockingJoin");
  }

  @Test
  public void blockingGet() throws ExecutionException, InterruptedException {
    FelineMetricsRecorder.install(registry);

    // blocking
    CompletableFuture.runAsync(RUNNABLE).get();

    assertMetric(getClass().getCanonicalName() + ".blockingGet");
  }

  @Test
  public void multipleBlockingGets() throws ExecutionException, InterruptedException {
    FelineMetricsRecorder.install(registry);

    blockfun1();
    blockfun2();

    assertMetric(getClass().getCanonicalName() + ".blockfun1");
    assertMetric(getClass().getCanonicalName() + ".blockfun2");
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
    FelineMetricsRecorder.install(registry);

    // blocking
    CompletableFuture.runAsync(RUNNABLE).get(10, TimeUnit.SECONDS);

    assertMetric(getClass().getCanonicalName() + ".blockingGetTimeout");
  }

  @Test
  public void blockingListenableFutureGet() throws Exception {
    FelineMetricsRecorder.install(registry);

    SettableFuture<Integer> lf = SettableFuture.create();

    CompletableFuture.runAsync(RUNNABLE).thenAccept(ignored -> lf.set(123));

    lf.get();

    assertMetric(getClass().getCanonicalName() + ".blockingListenableFutureGet");
  }

  @Test
  public void blockingListenableFutureGetTimeout() throws Exception {
    FelineMetricsRecorder.install(registry);

    SettableFuture<Integer> lf = SettableFuture.create();

    CompletableFuture.runAsync(RUNNABLE).thenAccept(ignored -> lf.set(123));

    lf.get(1, TimeUnit.SECONDS);

    assertMetric(getClass().getCanonicalName() + ".blockingListenableFutureGetTimeout");
  }

  private void assertMetric(final String call) {
    final String expectedThreadName =
        MetricsConsumer.sanitizeThreadName(Thread.currentThread().getName());

    expectMetric(BLOCKING_CALL_ID.tagged("call", call, "thread_name", expectedThreadName));
    expectMetric(BLOCKING_TIME_CALL_ID.tagged("call", call, "thread_name", expectedThreadName));
  }

  private void expectMetric(final MetricId expectedId) {
    assertTrue(
        "Did not find meter with id="
            + expectedId
            + ", all meters in registry: "
            + registry.getMeters().keySet(),
        registry.getMeters().containsKey(expectedId));
  }
}
