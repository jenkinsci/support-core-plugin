package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.model.NodeMonitors;
import com.cloudbees.jenkins.support.util.Helper;
import com.cloudbees.jenkins.support.util.SupportUtils;
import com.google.common.base.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.node_monitors.AbstractDiskSpaceMonitor;
import hudson.node_monitors.NodeMonitor;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;

/**
 * Created by schristou88 on 11/4/16.
 */
@Extension
public class NodeMonitorsComponent extends Component{
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Node monitors";
    }

    @Override
    public void addContents(@NonNull Container container) {
        NodeMonitors monitors = new NodeMonitors();
        for (NodeMonitor monitor : NodeMonitor.getAll()) {
            NodeMonitors.NodeMonitor nodeMonitor;

            if (monitor instanceof AbstractDiskSpaceMonitor) {
                nodeMonitor = new NodeMonitors.DiskSpaceMonitor();
                ((NodeMonitors.DiskSpaceMonitor) nodeMonitor).setFreeSpaceThreshold(((AbstractDiskSpaceMonitor) monitor).freeSpaceThreshold);
            } else {
                nodeMonitor = new NodeMonitors.NodeMonitor();
            }

            nodeMonitor.setColumnCaption(monitor.getColumnCaption());
            nodeMonitor.setIgnored(monitor.isIgnored());

            if (!monitor.isIgnored()) {
                for (Computer c : Helper.getActiveInstance().getComputers()) {
                    NodeMonitors.NodeMonitor.Computer comp = new NodeMonitors.NodeMonitor.Computer();
                    comp.setDisplayName(c.getDisplayName());
                    comp.setData(SupportUtils.trimToEmpty(monitor.data(c)));
                    nodeMonitor.addComputer(comp);
                }
            }

            monitors.addNodeMonitor(nodeMonitor);
        }
        container.add(new MarkdownContent("node-monitors.md", monitors));
        container.add(new YamlContent("node-monitors.yaml", monitors));
    }
}