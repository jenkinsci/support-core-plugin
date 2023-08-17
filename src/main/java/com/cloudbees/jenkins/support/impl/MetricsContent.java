package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Content;
import com.codahale.metrics.Clock;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * A metric content.
 *
 * @author Stephen Connolly
 */
public class MetricsContent extends Content {
    private final Clock clock = Clock.defaultClock();
    private MetricRegistry registry;
    private ObjectMapper objectMapper;

    public MetricsContent(String name, MetricRegistry metricsRegistry) {
        super(name);

        this.registry = metricsRegistry;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new MetricsModule(TimeUnit.MINUTES, TimeUnit.SECONDS, true));
        objectMapper.registerModule(new HealthCheckModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public MetricsContent(String name, String[] filterableParameters, MetricRegistry metricsRegistry) {
        super(name, filterableParameters);

        this.registry = metricsRegistry;
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new MetricsModule(TimeUnit.MINUTES, TimeUnit.SECONDS, true));
        objectMapper.registerModule(new HealthCheckModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        objectMapper.writer().writeValue(os, registry);
    }

    @Override
    public boolean shouldBeFiltered() {
        // The information of this content is not sensible, so it doesn't need to be filtered.
        return false;
    }
}
