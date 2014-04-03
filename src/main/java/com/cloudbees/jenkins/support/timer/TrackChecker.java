package com.cloudbees.jenkins.support.timer;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.Functions;
import hudson.model.PeriodicWork;

import java.lang.management.ThreadInfo;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class TrackChecker extends PeriodicWork {
    @Inject
    TrackerServletFilter filter;

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.SECONDS.toMillis(1);
    }

    @Override
    protected void doRun() throws Exception {
        ThreadInfo[] threads;
        threads = Functions.getThreadInfos();

        OUTER:
        for (InflightRequest req : filter.tracker.values()) {
            long stopTime = System.currentTimeMillis();
            long totalTime = stopTime - req.startTime;

            if (totalTime>1000) {
                if (threads==null)
                    threads = Functions.getThreadInfos();

                // if the thread has exited while we are taking the thread dump, ignore this.
                if (req.ended)    continue;

                for (ThreadInfo thread : threads) {
                    if (req.is(thread)) {
                        System.out.println(req.url + " took: " + totalTime + " milliseconds.");
                        for (StackTraceElement st : thread.getStackTrace()) {
                            System.out.println("    "+st);
                        }
                        continue OUTER;
                    }
                }
            }
        }
    }
}

