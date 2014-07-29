package com.cloudbees.jenkins.support.slowrequest;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.PrintWriter;
import java.util.Date;

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

    /**
     * When we start writing slow records, this field is set to non-null.
     */
    File record;


    InflightRequest(HttpServletRequest req) {
        url = req.getRequestURL().toString();
        startTime = System.currentTimeMillis();
    }

    void writeHeader(PrintWriter w) {
        w.println("Date: " + new Date());
        w.println("URL: " + url);
        w.println();
    }
}
