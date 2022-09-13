/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc
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

package com.cloudbees.jenkins.support.threaddump;

import com.cloudbees.jenkins.support.api.SupportContentContributor;
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.timer.FileListCap;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.PeriodicWork;
import jenkins.metrics.api.MetricProvider;
import jenkins.metrics.impl.VMMetricProviderImpl;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * PeriodicWork to check when there is a high load in the instance.
 *
 * Only checking high CPU usage for the moment, but it can be used
 * to generate thread dumps in high heap memory consumption.
 */
@Extension
public class HighLoadCpuChecker extends PeriodicWork implements SupportContentContributor {

    private static final String HIGH_LOAD_CPU_PATH_PROPERTY = HighLoadCpuChecker.class.getName() + ".highLoadCpuDir";

    /**
     * Recurrence period to check high cpu load consumption. Thread dumps after there are CONSECUTIVE_HIGH_CPU
     * in the RECURRENCE_PERIOD_SEC
     */
    public static final int RECURRENCE_PERIOD_SEC =
            Integer.getInteger(HighLoadCpuChecker.class.getName()+ ".RECURRENCE_PERIOD_SEC", 600);

    /**
     * Consecutive high CPUs to take a thread dump
     */
    public static final int HIGH_CPU_CONSECUTIVE_TIMES =
            Integer.getInteger(HighLoadCpuChecker.class.getName()+ ".HIGH_CPU_CONSECUTIVE_TIMES", 3);

    /**
     * This is the CPU usage threshold. Determinate de percentage of the total CPU used across all the
     * cores available
     */
    public static final Double CPU_USAGE_THRESHOLD =
            new Double(System.getProperty(HighLoadCpuChecker.class.getName() + ".CPU_USAGE_THRESHOLD", "0.80"));

    /**
     * Limit the number of thread dumps to retain on high cpu
     */
    public static final int HIGH_CPU_THREAD_DUMPS_TO_RETAIN =
            Integer.getInteger(HighLoadCpuChecker.class.getName()+ ".HIGH_CPU_THREAD_DUMPS_TO_RETAIN", 5);

    /**
     * Thread dumps generated on high CPU load are stored in $JENKINS_HOME/high-load/cpu
     **/
    protected final FileListCap logs = new FileListCap(new File(Jenkins.get().getRootDir(),"high-load/cpu"), HIGH_CPU_THREAD_DUMPS_TO_RETAIN);

    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
    {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private int countConsecutivePositives = 0;

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SEC);
    }

    @Override
    @SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "Intentional")
    protected void doRun() throws Exception {
        Double cpuLoad;
        try {
            VMMetricProviderImpl vmMetricProvider = ExtensionList.lookup(MetricProvider.class).get(VMMetricProviderImpl.class);
            MetricSet vmProviderMetricSet = vmMetricProvider.getMetricSet();
            Gauge cpu = (Gauge) vmProviderMetricSet.getMetrics().get("vm.cpu.load");
            cpuLoad = new Double(cpu.getValue().toString());
        } catch (NullPointerException nullPointerException) {
            LOGGER.log(WARNING, "Support Core plugin can't generate automatically thread dumps on high cpu load. Metrics plugin does not seem to be available", nullPointerException);
            return;
        }
        if (cpuLoad != null && Double.compare(Runtime.getRuntime().availableProcessors() * CPU_USAGE_THRESHOLD, cpuLoad) < 0) {
            countConsecutivePositives++;
            if (countConsecutivePositives >= HIGH_CPU_CONSECUTIVE_TIMES) {
                countConsecutivePositives = 0;
                    File threadDumpFile = logs.file(format.format(new Date()) + ".txt");
                    try (FileOutputStream fileOutputStream = new FileOutputStream(threadDumpFile)) {
                        ThreadDumps.threadDump(fileOutputStream);
                        logs.add(threadDumpFile);
                    }
            }
        } else {
            countConsecutivePositives = 0;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(HighLoadCpuChecker.class.getName());

    @NonNull
    @Override
    public File getDirPath() {
        String dirPath = SystemProperties.getString(HIGH_LOAD_CPU_PATH_PROPERTY);
        return dirPath == null
            ? new File(Jenkins.get().getRootDir(), "high-load/cpu")
            : new File(dirPath);
    }

    @CheckForNull
    @Override
    public FilenameFilter getFilenameFilter() {
        return (dir, name) -> name.endsWith(".txt");
    }

    @NonNull
    @Override
    public String getContributorId() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getContributorName() {
        return "High Load CPU Thread Dumps";
    }

    @Override
    public String getContributorDescription() {
        return "Thread Dumps collected when the instance is under CPU load";
    }
}
