package com.cloudbees.jenkins.support.slowrequest;

import static com.cloudbees.jenkins.support.slowrequest.SlowRequestThreadDumpsGenerator.MINIMAL_SLOW_REQUEST_COUNT;
import static java.util.logging.Level.WARNING;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.timer.FileListCap;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.PeriodicWork;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Run periodically to find slow requests and track them.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestChecker extends PeriodicWork {
    /**
     * How often to run the slow request checker
     * @since 2.12
     */
    public static final int RECURRENCE_PERIOD_SEC =
            Integer.getInteger(SlowRequestChecker.class.getName() + ".RECURRENCE_PERIOD_SEC", 3);

    /**
     * Time in milliseconds that's considered too slow for requests.
     * Starting with a bit conservative value to catch serious offenders first.
     * If this value is less than twice {@link #RECURRENCE_PERIOD_SEC} then that will be used instead.
     */
    public static final int THRESHOLD = Integer.getInteger(SlowRequestChecker.class.getName() + ".THRESHOLD_MS", 10000);

    /**
     * Provide a means to disable the slow request checker. This is a volatile non-final field as if you run into
     * issues in a running Jenkins you may need to disable without restarting Jenkins.
     *
     * @since 2.12
     */
    public static volatile boolean DISABLED = Boolean.getBoolean(SlowRequestChecker.class.getName() + ".DISABLED");

    @Inject
    SlowRequestFilter filter;

    final FileListCap logs = new FileListCap(new File(Jenkins.get().getRootDir(), "slow-requests"), 50);

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
        if (DISABLED || filter.tracker.isEmpty()) {
            return;
        }

        final long now = System.currentTimeMillis();

        long iota = System.currentTimeMillis();

        final long recurrencePeriosMillis = TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SEC);
        long thresholdMillis = recurrencePeriosMillis > THRESHOLD ? recurrencePeriosMillis * 2 : THRESHOLD;

        // We filter the information written to the slow-requests files
        ContentFilter contentFilter = SupportPlugin.getDefaultContentFilter();

        int slowRequestCount = 0;

        for (InflightRequest req : filter.tracker.values()) {
            long totalTime = now - req.startTime;

            if (totalTime > thresholdMillis) {
                // if the thread has exited while we are taking the thread dump, ignore this.
                if (req.ended) continue;

                boolean newRecord = req.record == null;

                slowRequestCount++;

                if (newRecord) {
                    req.record = logs.file(format.format(new Date(iota++)) + ".txt");
                    logs.add(req.record);
                } else {
                    logs.touch(req.record);
                }
                try (PrintWriter w = new PrintWriter(
                        new OutputStreamWriter(new FileOutputStream(req.record, !newRecord), StandardCharsets.UTF_8))) {
                    if (newRecord) {
                        req.writeHeader(w, contentFilter);
                    }
                    if (req.record.length() >= FileListCapComponent.MAX_FILE_SIZE) continue;
                    ThreadInfo lockedThread =
                            ManagementFactory.getThreadMXBean().getThreadInfo(req.thread.getId(), Integer.MAX_VALUE);
                    if (lockedThread != null) {
                        w.println(contentFilter.filter(lockedThread.toString()));
                        w.println(totalTime + "msec elapsed in " + contentFilter.filter(lockedThread.getThreadName()));
                        printThreadStackElements(lockedThread, w, contentFilter);

                        long lockOwnerId = lockedThread.getLockOwnerId();
                        if (lockOwnerId != -1) // If the thread is not locked, then getLockOwnerId returns -1.
                        {
                            ThreadInfo threadInfo =
                                    ManagementFactory.getThreadMXBean().getThreadInfo(lockOwnerId, Integer.MAX_VALUE);
                            w.println(contentFilter.filter(lockedThread.toString()));
                            if (threadInfo != null) {
                                printThreadStackElements(threadInfo, w, contentFilter);
                            }
                        }
                    }
                }
            }
        }

        if (slowRequestCount >= MINIMAL_SLOW_REQUEST_COUNT) {
            boolean newThreadDumps = SlowRequestThreadDumpsGenerator.checkThreadDumpsTrigger(iota);

            if (newThreadDumps) {
                try {
                    SlowRequestThreadDumpsGenerator slowRequestThreadDumpsGenerator =
                            new SlowRequestThreadDumpsGenerator(iota);
                    slowRequestThreadDumpsGenerator.start();
                } catch (Exception e) {
                    LOGGER.log(
                            WARNING,
                            "Support Core plugin can't throw a new thread to collect thread dumps under SlowRequest scenario",
                            e);
                }
            }
        }
    }

    private void printThreadStackElements(ThreadInfo threadinfo, PrintWriter writer, ContentFilter contentFilter) {
        for (StackTraceElement element : threadinfo.getStackTrace()) {
            writer.println("    " + contentFilter.filter(element.toString()));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SlowRequestChecker.class.getName());
}
