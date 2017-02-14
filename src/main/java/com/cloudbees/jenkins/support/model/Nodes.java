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
        About.VersionDetails versionDetails;
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Node statistics");
        out.println("===============");
        out.println();
        out.println("  * Total number of nodes");
    }
}
