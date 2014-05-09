package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.timer.FileListCap;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.Functions;
import hudson.model.PeriodicWork;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ThreadInfo;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Run periodically to find slow requests and track them.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestChecker extends PeriodicWork {
    @Inject
    SlowRequestFilter filter;

    @Inject
    Jenkins jenkins;

    final FileListCap logs = new FileListCap(new File(Jenkins.getInstance().getRootDir(),"slow-requests"), 50);

    final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(3);
    }

    @Override
    protected void doRun() throws Exception {
        ThreadInfo[] threads;
        threads = Functions.getThreadInfos();

        final long now = System.currentTimeMillis();

        long iota = System.currentTimeMillis();

        OUTER:
        for (InflightRequest req : filter.tracker.values()) {
            long totalTime = now - req.startTime;

            if (totalTime> THRESHOLD) {
                if (threads==null)
                    threads = Functions.getThreadInfos();

                // if the thread has exited while we are taking the thread dump, ignore this.
                if (req.ended)    continue;

                PrintWriter w = null;
                try {
                    if (req.record==null) {
                        req.record = logs.file(format.format(new Date(iota++)) + ".txt");
                        logs.add(req.record);

                        w = new PrintWriter(req.record,"UTF-8");
                        req.writeHeader(w);
                    } else {
                        w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(req.record,true),"UTF-8"));
                        logs.touch(req.record);
                    }

                    for (ThreadInfo thread : threads) {
                        if (req.is(thread)) {
                            w.println("TimeElapsed: " + totalTime + "ms");
                            for (StackTraceElement st : thread.getStackTrace()) {
                                w.println("    "+st);
                            }
                            continue OUTER;
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(w);
                }
            }
        }
    }

    /**
     * Time in milliseconds that's considered too slow for requests.
     * Starting with a bit conservative value to catch serious offenders first.
     */
    public static final int THRESHOLD = 10000;
}
