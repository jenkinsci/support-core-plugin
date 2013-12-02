package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Content;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.metrics.core.Clock;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Metered;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricProcessor;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Sampling;
import com.yammer.metrics.core.Summarizable;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.VirtualMachineMetrics;
import com.yammer.metrics.stats.Snapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A metric content.
 *
 * @author Stephen Connolly
 */
public class MetricsContent extends Content implements MetricProcessor<JsonGenerator> {
    private final Clock clock = Clock.defaultClock();
    private final VirtualMachineMetrics vm = VirtualMachineMetrics.getInstance();
    private MetricsRegistry registry;
    private JsonFactory factory = new JsonFactory(new ObjectMapper());
    private boolean showJvmMetrics = true;

    public MetricsContent(String name, MetricsRegistry metricsRegistry) {
        super(name);
        this.registry = metricsRegistry;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        final JsonGenerator json = factory.createJsonGenerator(os, JsonEncoding.UTF8);
        json.useDefaultPrettyPrinter();
        json.writeStartObject();
        {
            if (showJvmMetrics) {
                writeVmMetrics(json);
            }

            if (registry != null) {
                writeRegularMetrics(json, null, true);
            }
        }
        json.writeEndObject();
        json.close();
    }

    private void writeVmMetrics(JsonGenerator json) throws IOException {
        json.writeFieldName("jvm");
        json.writeStartObject();
        {
            json.writeFieldName("vm");
            json.writeStartObject();
            {
                json.writeStringField("name", vm.name());
                json.writeStringField("version", vm.version());
            }
            json.writeEndObject();

            json.writeFieldName("memory");
            json.writeStartObject();
            {
                json.writeNumberField("totalInit", vm.totalInit());
                json.writeNumberField("totalUsed", vm.totalUsed());
                json.writeNumberField("totalMax", vm.totalMax());
                json.writeNumberField("totalCommitted", vm.totalCommitted());

                json.writeNumberField("heapInit", vm.heapInit());
                json.writeNumberField("heapUsed", vm.heapUsed());
                json.writeNumberField("heapMax", vm.heapMax());
                json.writeNumberField("heapCommitted", vm.heapCommitted());

                json.writeNumberField("heap_usage", vm.heapUsage());
                json.writeNumberField("non_heap_usage", vm.nonHeapUsage());
                json.writeFieldName("memory_pool_usages");
                json.writeStartObject();
                {
                    for (Map.Entry<String, Double> pool : vm.memoryPoolUsage().entrySet()) {
                        json.writeNumberField(pool.getKey(), pool.getValue());
                    }
                }
                json.writeEndObject();
            }
            json.writeEndObject();

            final Map<String, VirtualMachineMetrics.BufferPoolStats> bufferPoolStats = vm.getBufferPoolStats();
            if (!bufferPoolStats.isEmpty()) {
                json.writeFieldName("buffers");
                json.writeStartObject();
                {
                    json.writeFieldName("direct");
                    json.writeStartObject();
                    {
                        json.writeNumberField("count", bufferPoolStats.get("direct").getCount());
                        json.writeNumberField("memoryUsed", bufferPoolStats.get("direct").getMemoryUsed());
                        json.writeNumberField("totalCapacity", bufferPoolStats.get("direct").getTotalCapacity());
                    }
                    json.writeEndObject();

                    json.writeFieldName("mapped");
                    json.writeStartObject();
                    {
                        json.writeNumberField("count", bufferPoolStats.get("mapped").getCount());
                        json.writeNumberField("memoryUsed", bufferPoolStats.get("mapped").getMemoryUsed());
                        json.writeNumberField("totalCapacity", bufferPoolStats.get("mapped").getTotalCapacity());
                    }
                    json.writeEndObject();
                }
                json.writeEndObject();
            }


            json.writeNumberField("daemon_thread_count", vm.daemonThreadCount());
            json.writeNumberField("thread_count", vm.threadCount());
            json.writeNumberField("current_time", clock.time());
            json.writeNumberField("uptime", vm.uptime());
            json.writeNumberField("fd_usage", vm.fileDescriptorUsage());

            json.writeFieldName("thread-states");
            json.writeStartObject();
            {
                for (Map.Entry<Thread.State, Double> entry : vm.threadStatePercentages()
                        .entrySet()) {
                    json.writeNumberField(entry.getKey().toString().toLowerCase(),
                            entry.getValue());
                }
            }
            json.writeEndObject();

            json.writeFieldName("garbage-collectors");
            json.writeStartObject();
            {
                for (Map.Entry<String, VirtualMachineMetrics.GarbageCollectorStats> entry : vm.garbageCollectors()
                        .entrySet()) {
                    json.writeFieldName(entry.getKey());
                    json.writeStartObject();
                    {
                        final VirtualMachineMetrics.GarbageCollectorStats gc = entry.getValue();
                        json.writeNumberField("runs", gc.getRuns());
                        json.writeNumberField("time", gc.getTime(TimeUnit.MILLISECONDS));
                    }
                    json.writeEndObject();
                }
            }
            json.writeEndObject();
        }
        json.writeEndObject();
    }

    public void writeRegularMetrics(JsonGenerator json, String classPrefix, boolean showFullSamples)
            throws IOException {
        for (Map.Entry<String, SortedMap<MetricName, Metric>> entry : registry.groupedMetrics().entrySet()) {
            if (classPrefix == null || entry.getKey().startsWith(classPrefix)) {
                json.writeFieldName(entry.getKey());
                json.writeStartObject();
                {
                    for (Map.Entry<MetricName, Metric> subEntry : entry.getValue().entrySet()) {
                        json.writeFieldName(subEntry.getKey().getName());
                        try {
                            subEntry.getValue()
                                    .processWith(this,
                                            subEntry.getKey(),
                                            json);
                        } catch (Exception e) {
                            LogRecord logRecord = new LogRecord(Level.WARNING, "Error writing out {0}");
                            logRecord.setThrown(e);
                            logRecord.setParameters(new Object[]{subEntry.getKey()});
                            Logger.getLogger(Metrics.class.getName()).log(logRecord);
                        }
                    }
                }
                json.writeEndObject();
            }
        }
    }

    public void processHistogram(MetricName name, Histogram histogram, JsonGenerator json) throws Exception {
        json.writeStartObject();
        {
            json.writeStringField("type", "histogram");
            json.writeNumberField("count", histogram.count());
            writeSummarizable(histogram, json);
            writeSampling(histogram, json);

            json.writeObjectField("values", histogram.getSnapshot().getValues());
        }
        json.writeEndObject();
    }

    public void processCounter(MetricName name, Counter counter, JsonGenerator json) throws Exception {
        json.writeStartObject();
        {
            json.writeStringField("type", "counter");
            json.writeNumberField("count", counter.count());
        }
        json.writeEndObject();
    }

    public void processGauge(MetricName name, Gauge<?> gauge, JsonGenerator json) throws Exception {
        json.writeStartObject();
        {
            json.writeStringField("type", "gauge");
            json.writeObjectField("value", evaluateGauge(gauge));
        }
        json.writeEndObject();
    }

    public void processMeter(MetricName name, Metered meter, JsonGenerator json) throws Exception {
        json.writeStartObject();
        {
            json.writeStringField("type", "meter");
            json.writeStringField("event_type", meter.eventType());
            writeMeteredFields(meter, json);
        }
        json.writeEndObject();
    }

    public void processTimer(MetricName name, Timer timer, JsonGenerator json) throws Exception {
        json.writeStartObject();
        {
            json.writeStringField("type", "timer");
            json.writeFieldName("duration");
            json.writeStartObject();
            {
                json.writeStringField("unit", timer.durationUnit().toString().toLowerCase());
                writeSummarizable(timer, json);
                writeSampling(timer, json);
                json.writeObjectField("values", timer.getSnapshot().getValues());
            }
            json.writeEndObject();

            json.writeFieldName("rate");
            json.writeStartObject();
            {
                writeMeteredFields(timer, json);
            }
            json.writeEndObject();
        }
        json.writeEndObject();
    }

    private static Object evaluateGauge(Gauge<?> gauge) {
        try {
            return gauge.value();
        } catch (RuntimeException e) {
            LogRecord logRecord = new LogRecord(Level.WARNING, "Error evaluating guage");
            logRecord.setThrown(e);
            Logger.getLogger(Metrics.class.getName()).log(logRecord);
            return "error reading gauge: " + e.getMessage();
        }
    }

    private static void writeSummarizable(Summarizable metric, JsonGenerator json) throws IOException {
        json.writeNumberField("min", metric.min());
        json.writeNumberField("max", metric.max());
        json.writeNumberField("mean", metric.mean());
        json.writeNumberField("std_dev", metric.stdDev());
    }

    private static void writeSampling(Sampling metric, JsonGenerator json) throws IOException {
        final Snapshot snapshot = metric.getSnapshot();
        json.writeNumberField("median", snapshot.getMedian());
        json.writeNumberField("p75", snapshot.get75thPercentile());
        json.writeNumberField("p95", snapshot.get95thPercentile());
        json.writeNumberField("p98", snapshot.get98thPercentile());
        json.writeNumberField("p99", snapshot.get99thPercentile());
        json.writeNumberField("p999", snapshot.get999thPercentile());
    }

    private static void writeMeteredFields(Metered metered, JsonGenerator json) throws IOException {
        json.writeStringField("unit", metered.rateUnit().toString().toLowerCase());
        json.writeNumberField("count", metered.count());
        json.writeNumberField("mean", metered.meanRate());
        json.writeNumberField("m1", metered.oneMinuteRate());
        json.writeNumberField("m5", metered.fiveMinuteRate());
        json.writeNumberField("m15", metered.fifteenMinuteRate());
    }
}
