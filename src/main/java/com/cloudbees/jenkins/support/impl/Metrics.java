package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.IOException2;
import jenkins.model.Jenkins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Metrics from the different nodes.
 *
 * @author Stephen Connolly
 */
@Extension
public class Metrics extends Component {

    private static final String UNAVAILABLE = "\"N/A\"";

    private final WeakHashMap<Node, byte[]> metricsCache = new WeakHashMap<Node, byte[]>();

    @Override
    @NonNull
    public String getDisplayName() {
        return "Metrics";
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        // TODO was originally no permissions, but that seems iffy
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new MetricsContent("nodes/master/metrics.json", jenkins.metrics.api.Metrics.metricRegistry()));
        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(new RemoteMetricsContent("nodes/slave/" + node.getNodeName() + "/metrics.json", node,
                    metricsCache));
        }
    }

    private static class RemoteMetricsContent extends Content {

        private final Node node;
        private final WeakHashMap<Node, byte[]> metricsCache;

        public RemoteMetricsContent(String name, Node node, WeakHashMap<Node, byte[]> metricsCache) {
            super(name);
            this.node = node;
            this.metricsCache = metricsCache;
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            os.write(AsyncResultCache.get(node, metricsCache, new GetMetricsResult(), "metrics data",
                    UNAVAILABLE.getBytes("utf-8")));
        }

    }

    private static class GetMetricsResult implements Callable<byte[], RuntimeException> {
        public byte[] call() throws RuntimeException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                // TODO pick up a per-slave metrics registry
                new MetricsContent("", null).writeTo(bos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return bos.toByteArray();
        }
    }

}
