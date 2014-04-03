package com.cloudbees.jenkins.support.timer;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.PeriodicWork;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
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
        for (Tag t : filter.tracker.values()) {
            long stopTime = System.currentTimeMillis();
            long totalTime = stopTime - t.startTime;
            System.out.println(t.url + " took: " + totalTime + " milliseconds.");
            
        }
    }


}

