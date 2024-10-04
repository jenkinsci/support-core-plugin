package com.cloudbees.jenkins.support.slowrequest;

import com.google.inject.Injector;
import hudson.Extension;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jenkins.model.Jenkins;

/**
 * Marks the beginning and the end of the request processing so that {@link SlowRequestChecker} can find them.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestFilter implements Filter {
    final ConcurrentMap<Thread, InflightRequest> tracker = new ConcurrentHashMap<Thread, InflightRequest>();

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (SlowRequestChecker.DISABLED) {
            chain.doFilter(request, response);
        } else {
            Thread t = Thread.currentThread();
            InflightRequest req = new InflightRequest((HttpServletRequest) request);
            tracker.put(t, req);
            try {
                chain.doFilter(request, response);
            } finally {
                req.ended = true;
                tracker.remove(t);
            }
        }
    }

    public void init(FilterConfig filterConfig) throws ServletException {}

    public void destroy() {}

    @Initializer
    public static void init() throws ServletException {
        Injector inj = Jenkins.get().getInjector();
        if (inj == null) {
            return;
        }
        PluginServletFilter.addFilter(inj.getInstance(SlowRequestFilter.class));
    }
}
