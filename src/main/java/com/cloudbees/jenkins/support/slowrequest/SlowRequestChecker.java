package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.SupportContentContributor;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.timer.FileListCap;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.PeriodicWork;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Run periodically to find slow requests and track them.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestChecker extends PeriodicWork implements SupportContentContributor {

    private static final String SLOW_REQUESTS_PATH_PROPERTY = SlowRequestChecker.class.getName() + ".slowRequestsDir";
    
    /**
     * How often to run the slow request checker
     * @since 2.12
     */
    public static final int RECURRENCE_PERIOD_SEC =
            Integer.getInteger(SlowRequestChecker.class.getName()+".RECURRENCE_PERIOD_SEC", 3);

    /**
     * Time in milliseconds that's considered too slow for requests.
     * Starting with a bit conservative value to catch serious offenders first.
     * If this value is less than twice {@link #RECURRENCE_PERIOD_SEC} then that will be used instead.
     */
    public static final int THRESHOLD =
            Integer.getInteger(SlowRequestChecker.class.getName()+".THRESHOLD_MS", 10000);

    /**
     * Provide a means to disable the slow request checker. This is a volatile non-final field as if you run into
     * issues in a running Jenkins you may need to disable without restarting Jenkins.
     *
     * @since 2.12
     */
    public static volatile boolean DISABLED = Boolean.getBoolean(SlowRequestChecker.class.getName()+".DISABLED");

    @Inject
    SlowRequestFilter filter;

    final FileListCap logs = new FileListCap(getDirPath(), 50);

    final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
    {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SEC);
    }

    @Override
    protected void doRun() throws Exception {
        if (DISABLED) {
            return;
        }

        // We filter the information written to the slow-requests files
        Optional<ContentFilter> contentFilter = SupportPlugin.getContentFilter();

        final long now = System.currentTimeMillis();

        long iota = System.currentTimeMillis();

        final long recurrencePeriosMillis = TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SEC);
        long thresholdMillis = recurrencePeriosMillis > THRESHOLD ?
                recurrencePeriosMillis * 2 : THRESHOLD;

        for (InflightRequest req : filter.tracker.values()) {
            long totalTime = now - req.startTime;

            if (totalTime> thresholdMillis) {
                // if the thread has exited while we are taking the thread dump, ignore this.
                if (req.ended)    continue;

                boolean newRecord = req.record == null;
                if(newRecord) {
                    req.record = logs.file(format.format(new Date(iota++)) + ".txt");
                    logs.add(req.record);
                } else {
                    logs.touch(req.record);
                }
                try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(req.record, !newRecord), StandardCharsets.UTF_8))) {
                    if(newRecord) {
                        req.writeHeader(w, contentFilter);
                    }
                    if (req.record.length() >= FileListCapComponent.MAX_FILE_SIZE)
                        continue;
                    ThreadInfo lockedThread = ManagementFactory.getThreadMXBean().getThreadInfo(req.thread.getId(), Integer.MAX_VALUE);
                    if (lockedThread != null ) {
                        w.println(contentFilter.map(cf -> cf.filter(lockedThread.toString())).orElse(lockedThread.toString()));
                        w.println(totalTime + "msec elapsed in " + contentFilter.map(cf -> cf.filter(lockedThread.getThreadName())).orElse(lockedThread.getThreadName()));
                        printThreadStackElements(lockedThread, w, contentFilter);

                        long lockOwnerId = lockedThread.getLockOwnerId();
                        if (lockOwnerId != -1) // If the thread is not locked, then getLockOwnerId returns -1.
                        {
                            ThreadInfo threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(lockOwnerId, Integer.MAX_VALUE);
                            w.println(contentFilter.map(cf -> cf.filter(lockedThread.toString())).orElse(lockedThread.toString()));
                            if (threadInfo != null) {
                                printThreadStackElements(threadInfo, w, contentFilter);
                            }
                        }
                    }
                }
            }
        }
    }

    private void printThreadStackElements(ThreadInfo threadinfo, PrintWriter writer, Optional<ContentFilter> contentFilter) {
        for (StackTraceElement element : threadinfo.getStackTrace()) {
            writer.println("    " + contentFilter.map(cf -> cf.filter(element.toString())).orElse(element.toString()));
        }
    }

    @NonNull
    @Override
    public File getDirPath() {
        String slowRequestsPath = SystemProperties.getString(SLOW_REQUESTS_PATH_PROPERTY);
        return slowRequestsPath == null
            ? new File(Jenkins.get().getRootDir(), "slow-requests")
            : new File(slowRequestsPath);
    }

    @CheckForNull
    @Override
    public FilenameFilter getFilenameFilter() {
        return (dir, name) -> name.endsWith(".txt");
    }

    @Override
    public String getContributorName() {
        return "Slow Requests";
    }

    @NonNull
    @Override
    public String getContributorDescription() {
        return "Slow HTTP requests dump generated when a requests take more than a certain amount of time";
    }
    
}
