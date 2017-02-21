package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/17/17.
 */
@Data
public class NodeMonitors implements Serializable, MarkdownFile{
    List<NodeMonitor> monitorList = new ArrayList<>();


    @Data
    public static class NodeMonitor {
        String columnCaption;
        boolean isIgnored;
        List<Computer> computersList;

        @Data
        public static class Computer {
            String displayName;
            String data;
        }
    }

    @Data
    public static class DiskSpaceMonitor extends NodeMonitor {
        String freeSpaceThreshold;
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Node monitors");
        out.println("=============");
        for (NodeMonitor monitor : monitorList) {
            out.println(monitor.getColumnCaption());
            out.println("----");
            out.println(" - Is Ignored: " + monitor.isIgnored());
            if (monitor instanceof DiskSpaceMonitor) {
                out.println(" - Threshold: " + ((DiskSpaceMonitor)monitor).freeSpaceThreshold);
            }
            if (!monitor.isIgnored()) {
                out.println(" - Computers:");
                for (NodeMonitor.Computer c : monitor.getComputersList()) {
                    out.println("   * " + c.getDisplayName() + ": " + c.getData());
                }
            }
        }
    }
}
