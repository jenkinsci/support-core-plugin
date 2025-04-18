/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc
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
package com.cloudbees.jenkins.support.startup;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.timer.FileListCap;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.listeners.ItemListener;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;

/**
 * Collects thread dumps during shutdown if it exceeds a certain threshold.
 */
@Extension
public final class ShutdownComponent extends UnfilteredFileListCapComponent {
    private static final Logger LOGGER = Logger.getLogger(ShutdownComponent.class.getName());

    private static final int THREAD_DUMPS_TO_RETAIN =
            SystemProperties.getInteger(ShutdownComponent.class.getName() + ".THREAD_DUMPS_TO_RETAIN", 40);
    private static final int TOTAL_ITERATIONS =
            SystemProperties.getInteger(ShutdownComponent.class.getName() + ".TOTAL_ITERATIONS", 4);
    private static final int DELAY_BETWEEN_THREAD_DUMPS_MS =
            SystemProperties.getInteger(ShutdownComponent.class.getName() + ".DELAY_BETWEEN_THREAD_DUMPS_MS", 1000);
    public static final int INITIAL_DELAY_SECONDS =
            SystemProperties.getInteger(ShutdownComponent.class.getName() + ".INITIAL_DELAY_SECONDS", 15);

    private final FileListCap logs =
            new FileListCap(new File(SupportPlugin.getRootDirectory(), "shutdown-threaddumps"), THREAD_DUMPS_TO_RETAIN);

    @NonNull
    @Override
    public String getDisplayName() {
        return "Shutdown thread dumps";
    }

    @Override
    public void addContents(@NonNull Container container) {
        super.addContents(container, logs);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    public FileListCap getLogs() {
        return logs;
    }

    @Extension
    public static class OnShutdown extends ItemListener {
        @Override
        public void onBeforeShutdown() {
            var logs = ShutdownComponent.get().logs;
            Timer.get()
                    .schedule(
                            () -> {
                                LOGGER.log(Level.FINE, () -> "Collecting shutdown thread dumps");
                                ;
                                try {
                                    ThreadDumps.collectMultiple(
                                            logs,
                                            System.currentTimeMillis(),
                                            DELAY_BETWEEN_THREAD_DUMPS_MS,
                                            TOTAL_ITERATIONS,
                                            Level.FINE);
                                } catch (Exception e) {
                                    LOGGER.log(Level.WARNING, "Failed to collect thread dumps", e);
                                }
                            },
                            INITIAL_DELAY_SECONDS,
                            TimeUnit.SECONDS);
        }
    }

    public static ShutdownComponent get() {
        return ExtensionList.lookupSingleton(ShutdownComponent.class);
    }
}
