package com.cloudbees.jenkins.support.timer;

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
 * @author Kohsuke Kawaguchi
 */
@Extension
public class TrackerServletFilter implements Filter {
    final ConcurrentMap<Thread,Tag> tracker = new ConcurrentHashMap<Thread, Tag>();
//    private ThreadLocal<Tag> tracker = new ThreadLocal<Tag>();

    public void init(FilterConfig filterConfig) throws ServletException {

    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Thread t = Thread.currentThread();
        tracker.put(t, new Tag((HttpServletRequest) request));
        try {
            chain.doFilter(request,response);
        } finally {
            tracker.remove(t);
        }
    }

    public void destroy() {
    }

    @Initializer
    public static void init() throws ServletException {
        PluginServletFilter.addFilter(Jenkins.getInstance().getInjector().getInstance(TrackerServletFilter.class));
    }
}
