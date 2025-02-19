/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import jenkins.util.JenkinsJVM;

public class SafeLog {
    public static final Path file;

    static {
        // Cannot use System.getProperty("java.io.tmpdir") since this has different values
        // for test/controller JVM vs. agent (due to override in plugin POM).
        try {
            var tmp = Files.createTempFile(null, null);
            Files.delete(tmp);
            file = tmp.getParent().resolve("safe.log");
        } catch (IOException x) {
            throw new AssertionError(x);
        }
    }

    public static void print(String message) {
        // TODO FileChannel.open + .lock if necessary to synchronize access
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(Instant.now() + " [" + (JenkinsJVM.isJenkinsJVM() ? "J" : "A") + "] " + message + "\n");
        } catch (IOException x) {
            x.printStackTrace();
        }
    }

    private SafeLog() {}
}
