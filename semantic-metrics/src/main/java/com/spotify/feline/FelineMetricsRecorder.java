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

import com.codahale.metrics.Meter;
import com.spotify.metrics.core.MetricId;
import com.spotify.metrics.core.SemanticMetricBuilder;
import com.spotify.metrics.core.SemanticMetricRegistry;
import java.util.Optional;

/** Feline consumer that integrates with semantic-metrics. */
public class FelineMetricsRecorder {

  private final SemanticMetricRegistry registry;
  private final MetricId blockingCallId = MetricId.EMPTY.tagged("what", "blocking-calls");
  private final MetricId timeBlockedCallId =
      MetricId.EMPTY.tagged("what", "blocking-calls-time", "unit", "ns");
  private final MetricsConsumer.CallFinder callFinder;

  private final Meter initialValueCalls;

  /**
   * Create a MetricsConsumer with the default Predicate. The default Predicate will tag the metric
   * with first method name not matching "java." after the blocking call. This will typically give a
   * good indication of what code is triggering the blocking call.
   */
  private FelineMetricsRecorder(final SemanticMetricRegistry registry) {
    this(registry, new MetricsConsumer.DefaultCallFinder());
  }

  /**
   * Create a MetricsConsumer with a custom Predicate. This will tag the metric with the first
   * method matching the Predicate. This allows to filter out class or method names that is not
   * useful to identify the caller of the blocking call.
   *
   * <p>The custom Predicate will be invoked on stack trace elements before the blocking call.
   */
  public FelineMetricsRecorder(
      final SemanticMetricRegistry registry, final MetricsConsumer.CallFinder callFinder) {
    this.registry = registry;
    this.callFinder = callFinder;
    this.initialValueCalls =
        registry.meter(MetricId.EMPTY.tagged("what", "thread-local-initial-value"));
  }

  private Optional<StackTraceElement> getBlockingMethod(final String blockingCall) {
    final String blockingMethod = blockingCall.substring(0, blockingCall.indexOf('('));

    // the stackTrace array is top-down: the first element in the array is the most recent stack
    // frame
    final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length == 0) {
      // nothing to find
      return Optional.empty();
    }

    return callFinder.findCall(stackTraceElements, blockingMethod);
  }

  public static void install(final SemanticMetricRegistry registry) {
    install(new FelineMetricsRecorder(registry));
  }

  public static void install(
      final SemanticMetricRegistry registry, final MetricsConsumer.CallFinder callFinder) {
    install(new FelineMetricsRecorder(registry, callFinder));
  }

  private static void install(final FelineMetricsRecorder consumer) {
    Feline.addOnExitConsumerFirst(
        map -> {
          final String method = (String) map.get("method");
          final long blockedTimeNanos = (Long) map.get("blockedTimeNanos");
          final String call =
              consumer
                  .getBlockingMethod(method)
                  .map(element -> element.getClassName() + "." + element.getMethodName())
                  .orElse("unknown");
          consumer.markMeter(call, blockedTimeNanos);
        });
    Feline.addThreadLocalInitialValueConsumer(consumer::acceptThreadLocal);
  }

  private void acceptThreadLocal() {
    initialValueCalls.mark();
  }

  private void markMeter(final String call, final long timeBlockedNanos) {
    // NOTE: if metric reporting is ever moved to a separate thread from the one that is calling the
    // blocking Future method, this will have to change - to pass the threadName as a parameter to
    // Consumers in general. getBlockingMethod() above will also have to change.
    final String threadName = MetricsConsumer.sanitizeThreadName(Thread.currentThread().getName());

    final MetricId metricId = blockingCallId.tagged("call", call, "thread_name", threadName);
    final Meter meter = registry.getOrAdd(metricId, SemanticMetricBuilder.METERS);
    meter.mark();

    final MetricId timeBlockedMetricId =
        timeBlockedCallId.tagged("call", call, "thread_name", threadName);
    final Meter timeBlockedMeter =
        registry.getOrAdd(timeBlockedMetricId, SemanticMetricBuilder.METERS);
    timeBlockedMeter.mark(timeBlockedNanos);
  }
}
