package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/17/17.
 */

public class NodeMonitors implements Serializable, MarkdownFile{
    List<NodeMonitor> monitorList = new ArrayList<>();

    public void addNodeMonitor(NodeMonitor nodeMonitor) {
        this.monitorList.add(nodeMonitor);
    }

    public List<NodeMonitor> getMonitorList() {
        return monitorList;
    }

    public void setMonitorList(List<NodeMonitor> monitorList) {
        this.monitorList = monitorList;
    }

    public static class NodeMonitor implements Serializable {
        String columnCaption;
        boolean isIgnored;
        List<Computer> computersList = new ArrayList<>();

        public void addComputer(Computer computer) {
            computersList.add(computer);
        }

        public String getColumnCaption() {
            return columnCaption;
        }

        public void setColumnCaption(String columnCaption) {
            this.columnCaption = columnCaption;
        }

        public boolean isIgnored() {
            return isIgnored;
        }

        public void setIgnored(boolean ignored) {
            isIgnored = ignored;
        }

        public List<Computer> getComputersList() {
            return computersList;
        }

        public void setComputersList(List<Computer> computersList) {
            this.computersList = computersList;
        }

        public static class Computer implements Serializable {
            String displayName;
            String data;

            public String getDisplayName() {
                return displayName;
            }

            public void setDisplayName(String displayName) {
                this.displayName = displayName;
            }

            public String getData() {
                return data;
            }

            public void setData(String data) {
                this.data = data;
            }
        }
    }


    public static class DiskSpaceMonitor extends NodeMonitor implements Serializable {
        String freeSpaceThreshold;

        public String getFreeSpaceThreshold() {
            return freeSpaceThreshold;
        }

        public void setFreeSpaceThreshold(String freeSpaceThreshold) {
            this.freeSpaceThreshold = freeSpaceThreshold;
        }
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
