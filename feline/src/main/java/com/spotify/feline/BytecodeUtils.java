/*-
 * -\-\-
 * futuristic-feline
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
 * Copyright (c) 2018-2019 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spotify.feline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.utility.OpenedClassReader;

class BytecodeUtils {

  static void injectBootstrapClasses(
      final Instrumentation instrumentation, final String... classNames) throws IOException {
    final File tempJarFile = File.createTempFile("feline", ".jar");
    tempJarFile.deleteOnExit();

    final ClassLoader classLoader = Feline.class.getClassLoader();
    try (final ZipOutputStream zipOutputStream =
        new ZipOutputStream(new FileOutputStream(tempJarFile))) {
      for (final String className : classNames) {
        final String classFile = className.replace(".", "/") + ".class";
        try (final InputStream inputStream = classLoader.getResourceAsStream(classFile)) {
          final ZipEntry entry = new ZipEntry(classFile);
          zipOutputStream.putNextEntry(entry);

          // write class file to ZIP
          final byte[] buffer = new byte[1024];
          int len;
          while ((len = inputStream.read(buffer)) != -1) {
            zipOutputStream.write(buffer, 0, len);
          }
        }

        zipOutputStream.closeEntry();
      }
    }
    instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(tempJarFile));
  }

  public static Method getMethod(final StackTraceElement stackTraceElement) throws Exception {
    final String stackTraceClassName = stackTraceElement.getClassName();
    final String stackTraceMethodName = stackTraceElement.getMethodName();
    final int stackTraceLineNumber = stackTraceElement.getLineNumber();
    final Class<?> stackTraceClass = Class.forName(stackTraceClassName);

    final String classFileResourceName =
        "/" + stackTraceClassName.replaceAll("\\.", "/") + ".class";
    final InputStream classFileStream = stackTraceClass.getResourceAsStream(classFileResourceName);

    if (classFileStream == null) {
      throw new RuntimeException(
          "Could not acquire the class file containing for the calling class");
    }

    final AtomicReference<String> methodDescriptorReference = new AtomicReference<>();
    try {
      final ClassReader classReader = new ClassReader(classFileStream);
      classReader.accept(
          new ClassVisitor(OpenedClassReader.ASM_API) {
            @Override
            public MethodVisitor visitMethod(
                int access,
                final String name,
                final String desc,
                String signature,
                String[] exceptions) {
              if (!name.equals(stackTraceMethodName)) {
                return null;
              } else {
                return new MethodVisitor(OpenedClassReader.ASM_API) {
                  @Override
                  public void visitLineNumber(int line, Label start) {
                    if (line == stackTraceLineNumber) {
                      methodDescriptorReference.set(desc);
                    }
                  }
                };
              }
            }
          },
          0);
    } finally {
      classFileStream.close();
    }

    String methodDescriptor = methodDescriptorReference.get();

    if (methodDescriptor == null) {
      throw new RuntimeException("Could not find line " + stackTraceLineNumber);
    }

    for (Method method : stackTraceClass.getDeclaredMethods()) {
      if (stackTraceMethodName.equals(method.getName())
          && methodDescriptor.equals(Type.getMethodDescriptor(method))) {
        return method;
      }
    }

    throw new RuntimeException("Could not find the calling method " + stackTraceMethodName);
  }
}
