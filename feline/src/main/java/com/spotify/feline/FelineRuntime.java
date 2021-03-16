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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class FelineRuntime {
  public static final ThreadLocal<Boolean> STATE = ThreadLocal.withInitial(() -> false);

  private static final List<Consumer<String>> blockingCallConsumers = new CopyOnWriteArrayList<>();

  public static void addConsumerFirst(final Consumer<String> blockingCallConsumer) {
    blockingCallConsumers.add(0, blockingCallConsumer);
  }

  public static void addConsumerLast(final Consumer<String> blockingCallConsumer) {
    blockingCallConsumers.add(blockingCallConsumer);
  }

  public static boolean removeConsumer(final Consumer<String> blockingCallConsumer) {
    return blockingCallConsumers.remove(blockingCallConsumer);
  }

  public static void clearConsumers() {
    blockingCallConsumers.clear();
  }

  public static void accept(final String blockingCall) {
    for (final Consumer<String> consumer : blockingCallConsumers) {
      consumer.accept(blockingCall);
    }
  }
}
