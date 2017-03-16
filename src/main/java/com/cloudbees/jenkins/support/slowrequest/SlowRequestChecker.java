package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.timer.FileListCap;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.cloudbees.jenkins.support.util.Helper;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.PeriodicWork;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Run periodically to find slow requests and track them.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestChecker extends PeriodicWork {
    final FileListCap logs = new FileListCap(new File(Helper.getActiveInstance().getRootDir(),"slow-requests"), 50);

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

    @Inject
    Jenkins jenkins;

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SEC);
    }

    @Override
    protected void doRun() throws Exception {
        if (DISABLED) {
            return;
        }

        final long now = System.currentTimeMillis();

        final long recurrencePeriosMillis = TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD_SEC);
        long thresholdMillis = recurrencePeriosMillis > THRESHOLD ?
                recurrencePeriosMillis * 2 : THRESHOLD;

        for (InflightRequest req : filter.tracker.values()) {
            long totalTime = now - req.startTime;

            if (totalTime> thresholdMillis) {
                if (req.ended)    continue;

                for(SlowRequest request : ExtensionList.lookup(SlowRequest.class)) {
                    request.doRun(req, totalTime, logs);
                }
            }
        }
    }
}
