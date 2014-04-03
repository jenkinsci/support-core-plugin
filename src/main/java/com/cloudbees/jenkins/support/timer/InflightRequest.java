package com.cloudbees.jenkins.support.timer;

import javax.servlet.http.HttpServletRequest;
import java.lang.management.ThreadInfo;

/**
 * Tracks the request handling in progress.
 *
 * @author Kohsuke Kawaguchi
 */
final class InflightRequest {
    /**
     * Thread that's processing the request
     */
    final Thread thread = Thread.currentThread();

    /**
     * When did this request processing start?
     */
    final long startTime;

    /**
     * Request URL being processed.
     */
    final String url;

    /**
     * Set to true when the request handling is completed.
     */
    volatile boolean ended;

    InflightRequest(HttpServletRequest req) {
        url = req.getRequestURL().toString();
        startTime = System.currentTimeMillis();
    }

    public boolean is(ThreadInfo t) {
        return thread.getId()==t.getThreadId();
    }
}
