package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.filter.ContentFilter;
import jenkins.model.Jenkins;
import net.sf.uadetector.service.UADetectorServiceFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Optional;

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
    private final String userAgent;

    /**
     * Locale of slow request
     */
    final String locale;

    InflightRequest(HttpServletRequest req) {
        String query = req.getQueryString();
        url = req.getRequestURL() + (query == null ? "" : "?" + query);
        startTime = System.currentTimeMillis();
        userName = Jenkins.getAuthentication().getName();
        referer = req.getHeader("Referer");
        userAgent = req.getHeader("User-Agent");
        locale = req.getLocale().toString();
    }

    void writeHeader(PrintWriter w) {
        writeHeader(w, Optional.empty());
    }

    void writeHeader(PrintWriter w, Optional<ContentFilter> filter) {
        w.println("Username: " + filter.map(contentFilter -> contentFilter.filter(userName)).orElse(userName));
        w.println("Referer: " + filter.map(contentFilter -> contentFilter.filter(referer)).orElse(referer));
        w.println("User Agent: " + (userAgent != null ? UADetectorServiceFactory.getResourceModuleParser().parse(userAgent) : null));
        w.println("Date: " + new Date());
        w.println("URL: " + filter.map(contentFilter -> contentFilter.filter(url)).orElse(url));
        w.println("Locale: " + locale);
        w.println();
    }
}
