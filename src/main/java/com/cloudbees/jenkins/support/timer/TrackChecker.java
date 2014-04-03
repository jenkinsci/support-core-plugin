package com.cloudbees.jenkins.support.timer;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.Functions;
import hudson.model.PeriodicWork;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ThreadInfo;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class TrackChecker extends PeriodicWork {
    @Inject
    TrackerServletFilter filter;

    @Inject
    Jenkins jenkins;

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(1);
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

            if (totalTime>1000) {
                if (threads==null)
                    threads = Functions.getThreadInfos();

                // if the thread has exited while we are taking the thread dump, ignore this.
                if (req.ended)    continue;

                PrintWriter w = null;
                try {
                    if (req.record==null) {
                        File dir = new File(jenkins.getRootDir(), "slow-requests");
                        dir.mkdirs();
                        req.record = new File(dir, "record" + (iota++) + ".txt");

                        w = new PrintWriter(new FileWriter(req.record));
                        req.writeHeader(w);
                    } else {
                        w = new PrintWriter(new FileWriter(req.record,true));
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
                    if (w!=null)
                        w.close();
                }
            }
        }
    }
}

