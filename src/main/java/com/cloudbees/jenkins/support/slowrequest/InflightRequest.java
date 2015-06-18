package com.cloudbees.jenkins.support.slowrequest;

import com.google.common.net.HttpHeaders;
import jenkins.model.Jenkins;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.kohsuke.stapler.StaplerRequest;

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

    /**
     * Username of user who made the http call.
     */
    final String userName;

    /**
     * Referer link to track any redirect urls.
     */
    final String referer;

    /**
     * User Agent that invoked the slow request.
     */
    final ReadableUserAgent userAgent;

    InflightRequest(HttpServletRequest req) {
        String query = req.getQueryString();
        url = req.getRequestURL() + (query == null ? "" : "?" + query);
        startTime = System.currentTimeMillis();
        userName = Jenkins.getAuthentication().getName();
        referer = req.getHeader("Referer");
        String agentHeader = req.getHeader("User-Agent");
        userAgent = agentHeader != null ? UADetectorServiceFactory.getResourceModuleParser().parse(agentHeader) : null;
    }

    void writeHeader(PrintWriter w) {
        w.println("Username: " + userName);
        w.println("Referer: " + referer);
        w.println("User Agent: " + userAgent);
        w.println("Date: " + new Date());
        w.println("URL: " + url);
        w.println();
    }
}
