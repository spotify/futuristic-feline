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
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Feline consumer that integrates with semantic-metrics. */
public class MetricsConsumer implements Consumer<String> {

  private static final Pattern THREAD_NAME_SANITIZE_PATTERN = Pattern.compile("\\d+");
  private final SemanticMetricRegistry registry;
  private final MetricId blockingCallId = MetricId.EMPTY.tagged("what", "blocking-calls");
  private final CallFinder callFinder;

  /**
   * Create a MetricsConsumer with the default Predicate. The default Predicate will tag the metric
   * with first method name not matching "java." after the blocking call. This will typically give a
   * good indication of what code is triggering the blocking call.
   */
  public MetricsConsumer(final SemanticMetricRegistry registry) {
    this(registry, new DefaultCallFinder());
  }

  /**
   * Create a MetricsConsumer with a custom Predicate. This will tag the metric with the first
   * method matching the Predicate. This allows to filter out class or method names that is not
   * useful to identify the caller of the blocking call.
   *
   * <p>The custom Predicate will be invoked on stack trace elements before the blocking call.
   */
  public MetricsConsumer(final SemanticMetricRegistry registry, final CallFinder callFinder) {
    this.registry = registry;
    this.callFinder = callFinder;
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

  @Override
  public void accept(final String blockingCall) {
    getBlockingMethod(blockingCall).ifPresent(this::markMeter);
  }

  private void markMeter(final StackTraceElement element) {
    final String call = element.getClassName() + "." + element.getMethodName();
    // NOTE: if metric reporting is ever moved to a separate thread from the one that is calling the
    // blocking Future method, this will have to change - to pass the threadName as a parameter to
    // Consumers in general. getBlockingMethod() above will also have to change.
    final String threadName = sanitizeThreadName(Thread.currentThread().getName());

    final MetricId metricId = blockingCallId.tagged("call", call, "thread_name", threadName);
    final Meter meter = registry.getOrAdd(metricId, SemanticMetricBuilder.METERS);
    meter.mark();
  }

  // Visible for testing
  static String sanitizeThreadName(final String name) {
    return THREAD_NAME_SANITIZE_PATTERN.matcher(name).replaceAll("N");
  }

  /**
   * CallFinder is used to determine which StackTraceElement in a stacktrace is the one to point to
   * in metrics emitted by MetricsConsumer.
   */
  @FunctionalInterface
  public interface CallFinder {

    /**
     * Given a stacktrace and the blocking call in that stacktrace, determine which
     * StackTraceElement to use as a tag in metrics.
     *
     * @param blockingCall the name of the class/method which will block, formatted as
     *     "className.methodName" - without parentheses or argument types
     * @return an Optional containing the StackTraceElement to use in metrics, or the empty value if
     *     nothing of interest is found.
     */
    Optional<StackTraceElement> findCall(
        StackTraceElement[] stackTraceElements, String blockingCall);
  }

  static class DefaultCallFinder implements CallFinder {

    @Override
    public Optional<StackTraceElement> findCall(
        final StackTraceElement[] stackTraceElements, final String blockingCall) {

      StackTraceElement nonJavaPkgFrame = null;
      boolean seenBlocking = false;

      for (final StackTraceElement element : stackTraceElements) {
        final String className = element.getClassName();
        if ((className + "." + element.getMethodName()).equals(blockingCall)) {
          seenBlocking = true;
          continue;
        }

        if (seenBlocking) {
          if (className.startsWith("com.spotify.")) {
            // can stop searching here
            return Optional.of(element);
          }

          if (!className.startsWith("java.") && nonJavaPkgFrame == null) {
            nonJavaPkgFrame = element;
          }
        }
      }

      // did not find a com.spotify frame - return the first non-java frame (if any)
      return Optional.ofNullable(nonJavaPkgFrame);
    }
  }
}
