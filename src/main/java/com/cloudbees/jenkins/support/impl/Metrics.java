package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;

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
    public void addContents(@NonNull Container result) {
        result.add(new MetricsContent("nodes/master/metrics.json", jenkins.metrics.api.Metrics.metricRegistry()));
        /* TODO pick up a per-agent metrics registry
        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(new RemoteMetricsContent("nodes/slave/" + node.getNodeName() + "/metrics.json", node,
                    metricsCache));
        }
        */
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }
}
