package com.cloudbees.jenkins.support.timer;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.SupportContentContributor;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.PeriodicWork;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

import java.io.File;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * @author Steven Chrisou
 */
@Extension
public class DeadlockTrackChecker extends PeriodicWork implements SupportContentContributor {

    private static final String DEADLOCKS_PATH_PROPERTY = DeadlockTrackChecker.class.getName() + ".deadlocksDir";

    final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss");
    {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    final FileListCap logs = new FileListCap(new File(Jenkins.get().getRootDir(),"deadlocks"), 50);

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(15);
    }

    @Override
    protected void doRun() throws Exception {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        long[] deadLocks;
        try {
            deadLocks = mbean.findDeadlockedThreads();
        } catch (UnsupportedOperationException x) {
            deadLocks = null;
        }

        if (deadLocks != null && deadLocks.length != 0) {
            File file = logs.file("DeadlockDetected-" + format.format(new Date()) + ".txt");
            logs.add(file);
            PrintWriter builder = new PrintWriter(file,"UTF-8");
            try {
                builder.println("==============");
                builder.println("Deadlock Found");
                builder.println("==============");
                ThreadInfo[] deadLockThreads = mbean.getThreadInfo(deadLocks, Integer.MAX_VALUE);

                ContentFilter contentFilter = SupportPlugin.getContentFilter().orElse(null);
                for (ThreadInfo threadInfo : deadLockThreads) {
                    try {
                        ThreadDumps.printThreadInfo(builder, threadInfo, mbean, contentFilter);
                    } catch (LinkageError e) {
                        builder.println(threadInfo);
                    }
                }
            } finally {
                builder.close();
            }
        }
    }
    
    @NonNull
    @Override
    public File getDirPath() {
        String dirPath = SystemProperties.getString(DEADLOCKS_PATH_PROPERTY);
        return dirPath == null
            ? new File(Jenkins.get().getRootDir(), "deadlocks")
            : new File(dirPath);
    }

    @CheckForNull
    @Override
    public FilenameFilter getFilenameFilter() {
        return (dir, name) -> name.startsWith("DeadlockDetected-") && name.endsWith(".txt");
    }

    @NonNull
    @Override
    public String getContributorId() {
        return this.getClass().getSimpleName();
    }

    @NonNull
    @Override
    public String getContributorName() {
        return "Deadlocks";
    }

    @NonNull
    @Override
    public String getContributorDescription() {
        return "Thread deadlocks dump generated when a thread deadlock is detected";
    }
}

