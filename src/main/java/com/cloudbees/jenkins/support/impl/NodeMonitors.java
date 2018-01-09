package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.node_monitors.AbstractDiskSpaceMonitor;
import hudson.node_monitors.NodeMonitor;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;

/**
 * Created by schristou88 on 11/4/16.
 */
@Extension
public class NodeMonitors extends Component{
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
        addContents(container, false);
    }

    @Override
    public void addContents(@NonNull Container container, boolean shouldAnonymize) {
        container.add(new PrintedContent("node-monitors.md", shouldAnonymize) {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                out.println("Node monitors");
                out.println("=============");
                try {
                    for (NodeMonitor monitor : NodeMonitor.getAll()) {
                        out.println(monitor.getColumnCaption());
                        out.println("----");
                        out.println(" - Is Ignored: " + monitor.isIgnored());
                        if (monitor instanceof AbstractDiskSpaceMonitor) {
                            out.println(" - Threshold: " + ((AbstractDiskSpaceMonitor) monitor).freeSpaceThreshold);
                        }
                        if (!monitor.isIgnored()) {
                            out.println(" - Computers:");
                            for (Computer c : Jenkins.getInstance().getComputers()) {
                                out.println("   * " + c.getDisplayName() + ": " + monitor.data(c));
                            }
                        }
                    }
                } finally {
                    out.flush();
                }
            }
        });
    }
}