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

import java.util.Arrays;
import org.junit.Assume;
import org.junit.Before;

public class SurefireTest extends JUnit4TestTemplate {

  @Before
  public void setUp() {
    // Maven Surefire plugin will add FelineRunListener automatically (see this module's pom.xml)
    Assume.assumeTrue(isRunningInSurefire());
  }

  private boolean isRunningInSurefire() {
    return Arrays.stream(Thread.currentThread().getStackTrace())
        .anyMatch(element -> element.getClassName().contains("org.apache.maven.surefire"));
  }
}
