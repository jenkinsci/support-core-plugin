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

import com.cloudbees.jenkins.support.api.SupportContext;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import jenkins.metrics.api.Metrics;

/**
 * The implementation of {@link SupportContext}
 *
 * @author Stephen Connolly
 * @deprecated usage removed
 */
@Deprecated
public class SupportContextImpl implements SupportContext {

    @Deprecated
    public SupportContextImpl() {
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    @Deprecated
    public MetricRegistry getMetricsRegistry() {
        return Metrics.metricRegistry();
    }

    /**
     * {@inheritDoc}
     * @deprecated
     */
    @Deprecated
    public HealthCheckRegistry getHealthCheckRegistry() {
        return Metrics.healthCheckRegistry();
    }

    @Deprecated
    public void shutdown() {
    }
}
