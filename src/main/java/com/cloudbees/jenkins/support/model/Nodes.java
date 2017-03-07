package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/13/17.
 */
@Data
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

    @Data
    public static class Histogram {
        double sampleSize;
        double median;
        double mean;
        double standardDeviation;
        long minimum;
        long maximum;
        double nintyFifthPercentile;
        double nintyNinthPercentile;
    }

    @Data
    public static class MasterNode extends Node { }

    @Data
    public static class Node {
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
