/*
 * The MIT License
 *
 * Copyright (c) 2014 schristou88
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
package com.cloudbees.jenkins.support.timer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author schristou88
 */
@WithJenkins
class DeadlockTest {

    private static void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void detectDeadlock(JenkinsRule j) throws Exception {
        File[] files = new File(j.getInstance().getRootDir(), "/deadlocks").listFiles();
        int initialCount = files == null ? 0 : files.length;
        Object object1 = new Object();
        Object object2 = new Object();
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                synchronized (object1) {
                    sleep();
                    firstMethod();
                }
            }

            void firstMethod() {
                secondMethod(20);
            }

            void secondMethod(int x) {
                if (x > 0) {
                    secondMethod(x - 1);
                } else {
                    synchronized (object2) {
                    }
                }
            }
        });

        Thread t2 = new Thread(() -> {
            synchronized (object2) {
                sleep();
                synchronized (object1) {
                }
            }
        });

        t1.start();
        try {
            t2.start();
            try {

                Thread.sleep(1000 * 5); // Wait 5 seconds, then execute deadlock checker.

                // Force call deadlock checker
                DeadlockTrackChecker dtc = new DeadlockTrackChecker();
                dtc.doRun();

                // Reason for >= 1 is because depending on where the test unit is executed the deadlock detection thread
                // could be

                // invoked twice.
                files = new File(j.getInstance().getRootDir(), "/deadlocks").listFiles();
                assertNotNull(files, "There should be at least one deadlock file");
                assertThat(
                        "A deadlock was detected and a new deadlock file created",
                        files.length,
                        greaterThan(initialCount));
                String text = FileUtils.readFileToString(files[initialCount], Charset.defaultCharset());
                assertThat(text, containsString("secondMethod"));
                assertThat(text, containsString("firstMethod"));
            } finally {
                t2.interrupt();
            }
        } finally {
            t1.interrupt();
        }
    }
}
