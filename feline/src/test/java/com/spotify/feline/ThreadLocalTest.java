/*
 * Copyright (c) 2022 Spotify AB
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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ThreadLocalTest {

  private static final AtomicInteger COUNTER = new AtomicInteger();
  private static final Runnable RUNNABLE = COUNTER::incrementAndGet;

  @BeforeAll
  public static void classSetUp() {
    Feline.addThreadLocalInitialValueConsumer(RUNNABLE);
  }

  @AfterAll
  public static void teardown() {
    Feline.removeThreadLocalInitialValueConsumer(RUNNABLE);
  }

  @Test
  void testThreadLocal() {
    final int before = COUNTER.get();
    final ThreadLocal<String> threadLocal = ThreadLocal.withInitial(() -> "");
    threadLocal.get();
    final int after = COUNTER.get();
    assertEquals(before + 1, after);
  }
}
