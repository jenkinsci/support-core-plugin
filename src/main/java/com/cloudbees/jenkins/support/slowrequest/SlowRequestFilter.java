package com.cloudbees.jenkins.support.slowrequest;

import hudson.Extension;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import jenkins.model.Jenkins;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Marks the beginning and the end of the request processing so that {@link SlowRequestChecker} can find them.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestFilter implements Filter {
    final ConcurrentMap<Thread,InflightRequest> tracker = new ConcurrentHashMap<Thread, InflightRequest>();

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Thread t = Thread.currentThread();
        InflightRequest req = new InflightRequest((HttpServletRequest) request);
        tracker.put(t, req);
        try {
            chain.doFilter(request,response);
        } finally {
            req.ended = true;
            tracker.remove(t);
        }
    }

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void destroy() {
    }

    @Initializer
    public static void init() throws ServletException {
        PluginServletFilter.addFilter(Jenkins.getInstance().getInjector().getInstance(SlowRequestFilter.class));
    }
}
