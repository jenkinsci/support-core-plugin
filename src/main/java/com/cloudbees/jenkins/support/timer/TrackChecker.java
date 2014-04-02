package com.cloudbees.jenkins.support.timer;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.PeriodicWork;

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
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    protected void doRun() throws Exception {
        for (Tag t : filter.tracker.values()) {
            // TODO
        }
    }
}

