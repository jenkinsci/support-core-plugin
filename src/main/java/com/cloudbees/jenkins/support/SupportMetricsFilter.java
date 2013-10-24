/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * The metrics filter.
 *
 * @author Stephen Connolly
 */
public class SupportMetricsFilter implements Filter {
    private final ConcurrentMap<Integer, Meter> metersByStatusCode;
    private final Meter otherMeter;
    private final Counter activeRequests;
    private final Timer requestTimer;

    /**
     * Creates a new instance of the filter.
     *
     * @param meterNamesByStatusCode A map, keyed by status code, of meter names that we are
     *                               interested in.
     * @param otherMetricName        The name used for the catch-all meter.
     */
    public SupportMetricsFilter(MetricsRegistry registry, Map<Integer, String> meterNamesByStatusCode,
                                String otherMetricName) {
        this.metersByStatusCode = new ConcurrentHashMap<Integer, Meter>(meterNamesByStatusCode
                .size());
        for (Map.Entry<Integer, String> entry : meterNamesByStatusCode.entrySet()) {
            metersByStatusCode.put(entry.getKey(),
                    registry.newMeter(HttpServlet.class,
                            entry.getValue(),
                            "responses",
                            TimeUnit.SECONDS));
        }
        this.otherMeter = registry.newMeter(HttpServlet.class,
                otherMetricName,
                "responses",
                TimeUnit.SECONDS);
        this.activeRequests = registry.newCounter(HttpServlet.class, "activeRequests");
        this.requestTimer = registry.newTimer(HttpServlet.class,
                "requests",
                TimeUnit.MILLISECONDS,
                TimeUnit.SECONDS);

    }

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void destroy() {
    }

    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            final StatusExposingServletResponse wrappedResponse =
                    new StatusExposingServletResponse((HttpServletResponse) response);
            activeRequests.inc();
            final TimerContext context = requestTimer.time();
            try {
                chain.doFilter(request, wrappedResponse);
            } finally {
                context.stop();
                activeRequests.dec();
                markMeterForStatusCode(wrappedResponse.getStatus());
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private void markMeterForStatusCode(int status) {
        final Meter metric = metersByStatusCode.get(status);
        if (metric != null) {
            metric.mark();
        } else {
            otherMeter.mark();
        }
    }

    private static class StatusExposingServletResponse extends HttpServletResponseWrapper {
        private int httpStatus;

        public StatusExposingServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int sc) throws IOException {
            httpStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            httpStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        public void setStatus(int sc) {
            httpStatus = sc;
            super.setStatus(sc);
        }

        public int getStatus() {
            return httpStatus;
        }
    }
}
