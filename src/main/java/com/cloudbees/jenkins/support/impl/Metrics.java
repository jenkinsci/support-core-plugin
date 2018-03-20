package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.ContentData;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.util.Collections;
import java.util.Set;

/**
 * Metrics from the different nodes.
 *
 * @author Stephen Connolly
 */
@Extension
public class Metrics extends Component {

    /*
    private static final String UNAVAILABLE = "\"N/A\"";

    private final WeakHashMap<Node, byte[]> metricsCache = new WeakHashMap<Node, byte[]>();
    */

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
    public void addContents(@NonNull Container container) {
        addContents(container, false);
    }

    @Override
    public void addContents(@NonNull Container result, boolean shouldAnonymize) {
        result.add(new MetricsContent(new ContentData("nodes/master/metrics.json", shouldAnonymize), jenkins.metrics.api.Metrics.metricRegistry()));
        /* TODO pick up a per-agent metrics registry
        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(new RemoteMetricsContent("nodes/slave/" + node.getNodeName() + "/metrics.json", node,
                    metricsCache));
        }
        */
    }

    /*
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

    private static class GetMetricsResult extends MasterToSlaveCallable<byte[], RuntimeException> {
        public byte[] call() throws RuntimeException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                new MetricsContent("", somethingHere).writeTo(bos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return bos.toByteArray();
        }
    }
    */

}
