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
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.timer.FileListCap;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.SaveableListener;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

/**
 * Builds up startup report by collecting thread dumps during startup as well as the timing for each milestone.
 */
@Extension
public final class StartupReport extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(StartupReport.class.getName());
    private static final int THREAD_DUMPS_TO_RETAIN =
            SystemProperties.getInteger(StartupReport.class.getName() + ".THREAD_DUMPS_TO_RETAIN", 40);
    private static final int TOTAL_ITERATIONS =
            SystemProperties.getInteger(StartupReport.class.getName() + ".TOTAL_ITERATIONS", 1);
    private static final int INITIAL_DELAY_SECONDS =
            SystemProperties.getInteger(StartupReport.class.getName() + ".INITIAL_DELAY_SECONDS", 300);
    private static final int RECURRENCE_PERIOD_SECONDS =
            SystemProperties.getInteger(StartupReport.class.getName() + ".RECURRENCE_PERIOD_SECONDS", 30);
    private static final int DELAY_BETWEEN_THREAD_DUMPS_MS =
            SystemProperties.getInteger(StartupReport.class.getName() + ".DELAY_BETWEEN_THREAD_DUMPS_MS", 1000);

    private final FileListCap logs =
            new FileListCap(new File(SupportPlugin.getRootDirectory(), "startup-threaddumps"), THREAD_DUMPS_TO_RETAIN);

    /**
     * Record the timestamp at which each milestone was reached.
     */
    private Map<InitMilestone, Instant> timesPerMilestone = new ConcurrentHashMap<>();

    public StartupReport() {
        super("Startup report");
    }

    @NonNull
    public static StartupReport get() {
        return ExtensionList.lookupSingleton(StartupReport.class);
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SECONDS);
    }

    @Override
    public long getInitialDelay() {
        return TimeUnit.SECONDS.toMillis(INITIAL_DELAY_SECONDS);
    }

    public Map<InitMilestone, Instant> getTimesPerMilestone() {
        return Map.copyOf(timesPerMilestone);
    }

    // Too bad we don't have a generic listener when reaching a new milestone

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public void onPluginsStarted() {
        onMilestone(InitMilestone.PLUGINS_STARTED);
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public void onExtensionsAugmented() {
        onMilestone(InitMilestone.EXTENSIONS_AUGMENTED);
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public void onSystemConfigLoaded() {
        onMilestone(InitMilestone.SYSTEM_CONFIG_LOADED);
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public void onSystemConfigAdapted() {
        onMilestone(InitMilestone.SYSTEM_CONFIG_ADAPTED);
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public void onJobLoaded() {
        onMilestone(InitMilestone.JOB_LOADED);
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void onJobConfigAdapted() {
        onMilestone(InitMilestone.JOB_CONFIG_ADAPTED);
    }

    private void onMilestone(InitMilestone milestone) {
        timesPerMilestone.put(milestone, Instant.now());
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        if (Jenkins.get().getInitLevel() == InitMilestone.COMPLETED) {
            return;
        }
        LOGGER.fine("Collecting thread dumps for startup report");
        ThreadDumps.collectMultiple(logs, System.currentTimeMillis(), DELAY_BETWEEN_THREAD_DUMPS_MS, TOTAL_ITERATIONS);
    }

    public FileListCap getLogs() {
        return logs;
    }

    /**
     * Records the initial Jenkins save just after completing the startup reactor.
     */
    @Extension
    public static class InitialJenkinsSave extends SaveableListener {
        private volatile boolean disabled;

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (disabled) {
                return;
            }
            synchronized (this) {
                // may have been disabled by another thread
                if (disabled) {
                    return;
                }
                if (o instanceof Jenkins && Jenkins.get().getInitLevel() == InitMilestone.COMPLETED) {
                    LOGGER.fine("Recording the initial Jenkins save");
                    get().timesPerMilestone.put(InitMilestone.COMPLETED, Instant.now());
                    disabled = true;
                }
            }
        }
    }
}
