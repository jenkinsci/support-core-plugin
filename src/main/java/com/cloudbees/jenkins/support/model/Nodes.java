package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Nodes implements Serializable, MarkdownFile {
    Histogram nodesTotalCount;
    Histogram nodesOnlineCount;
    Histogram executorTotalCount;
    Histogram executorUsedCount;
    List<Node> nodeList = new ArrayList<>();
    MasterNode masterNode;

    public void addNode(Node node) {
        nodeList.add(node);
    }

    public Histogram getNodesTotalCount() {
        return nodesTotalCount;
    }

    public void setNodesTotalCount(Histogram nodesTotalCount) {
        this.nodesTotalCount = nodesTotalCount;
    }

    public Histogram getNodesOnlineCount() {
        return nodesOnlineCount;
    }

    public void setNodesOnlineCount(Histogram nodesOnlineCount) {
        this.nodesOnlineCount = nodesOnlineCount;
    }

    public Histogram getExecutorTotalCount() {
        return executorTotalCount;
    }

    public void setExecutorTotalCount(Histogram executorTotalCount) {
        this.executorTotalCount = executorTotalCount;
    }

    public Histogram getExecutorUsedCount() {
        return executorUsedCount;
    }

    public void setExecutorUsedCount(Histogram executorUsedCount) {
        this.executorUsedCount = executorUsedCount;
    }

    public List<Node> getNodeList() {
        return nodeList;
    }

    public void setNodeList(List<Node> nodeList) {
        this.nodeList = nodeList;
    }

    public MasterNode getMasterNode() {
        return masterNode;
    }

    public void setMasterNode(MasterNode masterNode) {
        this.masterNode = masterNode;
    }

    public static class Histogram implements Serializable {
        double sampleSize;
        double median;
        double mean;
        double standardDeviation;
        long minimum;
        long maximum;
        double nintyFifthPercentile;
        double nintyNinthPercentile;

        public double getSampleSize() {
            return sampleSize;
        }

        public void setSampleSize(double sampleSize) {
            this.sampleSize = sampleSize;
        }

        public double getMedian() {
            return median;
        }

        public void setMedian(double median) {
            this.median = median;
        }

        public double getMean() {
            return mean;
        }

        public void setMean(double mean) {
            this.mean = mean;
        }

        public double getStandardDeviation() {
            return standardDeviation;
        }

        public void setStandardDeviation(double standardDeviation) {
            this.standardDeviation = standardDeviation;
        }

        public long getMinimum() {
            return minimum;
        }

        public void setMinimum(long minimum) {
            this.minimum = minimum;
        }

        public long getMaximum() {
            return maximum;
        }

        public void setMaximum(long maximum) {
            this.maximum = maximum;
        }

        public double getNintyFifthPercentile() {
            return nintyFifthPercentile;
        }

        public void setNintyFifthPercentile(double nintyFifthPercentile) {
            this.nintyFifthPercentile = nintyFifthPercentile;
        }

        public double getNintyNinthPercentile() {
            return nintyNinthPercentile;
        }

        public void setNintyNinthPercentile(double nintyNinthPercentile) {
            this.nintyNinthPercentile = nintyNinthPercentile;
        }
    }


    public static class MasterNode extends Node implements Serializable { }


    public static class Node implements Serializable {
        String name;
        String descriptor;
        String description;
        int numOfExecutors;
        String fsRoot;
        String labels;
        String mode;
        String usage;
        String version;
        String launchMethod;
        String availability;
        String status;
        String retentionStrategy;
        About.VersionDetails versionDetails;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public void setDescriptor(String descriptor) {
            this.descriptor = descriptor;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getNumOfExecutors() {
            return numOfExecutors;
        }

        public void setNumOfExecutors(int numOfExecutors) {
            this.numOfExecutors = numOfExecutors;
        }

        public String getFsRoot() {
            return fsRoot;
        }

        public void setFsRoot(String fsRoot) {
            this.fsRoot = fsRoot;
        }

        public String getLabels() {
            return labels;
        }

        public void setLabels(String labels) {
            this.labels = labels;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUsage() {
            return usage;
        }

        public void setUsage(String usage) {
            this.usage = usage;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getLaunchMethod() {
            return launchMethod;
        }

        public void setLaunchMethod(String launchMethod) {
            this.launchMethod = launchMethod;
        }

        public String getAvailability() {
            return availability;
        }

        public void setAvailability(String availability) {
            this.availability = availability;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRetentionStrategy() {
            return retentionStrategy;
        }

        public void setRetentionStrategy(String retentionStrategy) {
            this.retentionStrategy = retentionStrategy;
        }

        public About.VersionDetails getVersionDetails() {
            return versionDetails;
        }

        public void setVersionDetails(About.VersionDetails versionDetails) {
            this.versionDetails = versionDetails;
        }
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Node statistics");
        out.println("===============");
        out.println();
        out.println("  * Total number of nodes");
        printHistogram(out, getNodesTotalCount());
        out.println("  * Total number of nodes online");
        printHistogram(out, getNodesOnlineCount());
        out.println("  * Total number of executors");
        printHistogram(out, getExecutorTotalCount());
        out.println("  * Total number of executors in use");
        printHistogram(out, getExecutorUsedCount());
        out.println();
        out.println("Build Nodes");
        out.println("===========");
        out.println();
        out.println("  * master (Jenkins)");
        out.println("      - Description:    _" + masterNode.description.replaceAll("_", "&#95;") + "_");
        out.println("      - Executors:      " + masterNode.numOfExecutors);
        out.println("      - FS root:        `" + masterNode.fsRoot.replaceAll("`", "&#96;") + "`");
        out.println("      - Labels:         " + masterNode.labels);
        out.println("      - Usage:          `" + masterNode.mode + "`");
        out.println("      - Slave Version:  " + masterNode.version);
        out.print(masterNode.versionDetails.toMarkdown("      -", "          +"));
        out.println();
        for (Node node : nodeList) {
            out.println("  * " + node.name + " (" + node.descriptor + ")");
            out.println("      - Description:    _" + node.description.replaceAll("_", "&#95;") + "_");
            out.println("      - Executors:      " + node.numOfExecutors);
            out.println("      - Remote FS root: `" + node.fsRoot.replaceAll("`", "&#96;")+ "`");
            out.println("      - Labels:         " + node.labels);
            out.println("      - Usage:          `" + node.mode + "`");
            out.println("      - Launch method:  " + node.launchMethod);
            out.println("      - Availability:   " + node.retentionStrategy);
            if ("off-line".equalsIgnoreCase(node.status)) {
                out.println("      - Status:         off-line");
            } else {
                out.println("      - Status:         on-line");
                out.println("      - Version:        " + node.version);
                out.print(node.versionDetails.toMarkdown("      -", "          +"));
            }
            out.println();
        }
    }

    private void printHistogram(PrintWriter out, Histogram histogram) {
        out.println("      - Sample size:        " + histogram.getSampleSize());
        out.println("      - Average (mean):     " + histogram.getMean());
        out.println("      - Average (median):   " + histogram.getMedian());
        out.println("      - Standard deviation: " + histogram.getStandardDeviation());
        out.println("      - Minimum:            " + histogram.getMinimum());
        out.println("      - Maximum:            " + histogram.getMaximum());
        out.println("      - 95th percentile:    " + histogram.getNintyFifthPercentile());
        out.println("      - 99th percentile:    " + histogram.getNintyNinthPercentile());
    }
}
