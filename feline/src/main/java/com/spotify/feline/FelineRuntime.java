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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class FelineRuntime {
  public static final ThreadLocal<Boolean> STATE = ThreadLocal.withInitial(() -> false);

  private static final List<Consumer<String>> onEnterConsumers = new CopyOnWriteArrayList<>();

  private static final List<Consumer<Map<String, Object>>> onExitConsumers =
      new CopyOnWriteArrayList<>();

  private static final List<Runnable> threadLocalInitialValueConsumers =
      new CopyOnWriteArrayList<>();

  public static void addOnExitConsumerFirst(
      final Consumer<Map<String, Object>> blockingCallConsumer) {
    onExitConsumers.add(0, blockingCallConsumer);
  }

  public static void addConsumerFirst(final Consumer<String> blockingCallConsumer) {
    onEnterConsumers.add(0, blockingCallConsumer);
  }

  public static void addOnExitConsumerLast(
      final Consumer<Map<String, Object>> blockingCallConsumer) {
    onExitConsumers.add(blockingCallConsumer);
  }

  public static void addConsumerLast(final Consumer<String> blockingCallConsumer) {
    onEnterConsumers.add(blockingCallConsumer);
  }

  public static boolean removeConsumer(final Consumer<String> blockingCallConsumer) {
    return onEnterConsumers.remove(blockingCallConsumer);
  }

  public static boolean removeOnExitConsumer(
      final Consumer<Map<String, Object>> blockingCallConsumer) {
    return onExitConsumers.remove(blockingCallConsumer);
  }

  public static void clearConsumers() {
    onEnterConsumers.clear();
    onExitConsumers.clear();
  }

  public static void accept(final String blockingCall) {
    for (final Consumer<String> consumer : onEnterConsumers) {
      consumer.accept(blockingCall);
    }
  }

  public static void acceptOnExit(final Map<String, Object> data) {
    for (final Consumer<Map<String, Object>> consumer : onExitConsumers) {
      consumer.accept(data);
    }
  }

  public static void addThreadLocalInitialValueConsumer(final Runnable consumer) {
    threadLocalInitialValueConsumers.add(consumer);
  }

  public static boolean removeThreadLocalInitialValueConsumer(final Runnable consumer) {
    return threadLocalInitialValueConsumers.remove(consumer);
  }

  public static void acceptThreadLocalInitialValue() {
    // Since this method may be invoked frequently, the regular for-each loop is
    // replaced with a manual loop to reduce object creation
    final int n = threadLocalInitialValueConsumers.size();
    for (int i = 0; i < n; i++) {
      try {
        threadLocalInitialValueConsumers.get(i).run();
      } catch (Exception e) {
        // Ignore
      }
    }
  }
}
