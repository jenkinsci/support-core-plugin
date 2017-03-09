package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.model.About;
import com.cloudbees.jenkins.support.model.Nodes;
import com.cloudbees.jenkins.support.util.Helper;
import com.cloudbees.jenkins.support.util.SupportUtils;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NodesContent {

    private final WeakHashMap<Node,String> slaveVersionCache = new WeakHashMap<>();
    private final WeakHashMap<Node,About.VersionDetails> javaInfoCache = new WeakHashMap<>();

    private String getLabelString(Node n) {
        String r = n.getLabelString();
        return r.isEmpty() ? "(none)" : r;
    }

    public Nodes generate() {
        Nodes nodes = new Nodes();
        final Jenkins jenkins = Helper.getActiveInstance();
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            nodes.setNodesTotalCount(printHistogram(supportPlugin.getJenkinsNodeTotalCount()));
            nodes.setNodesOnlineCount(printHistogram(supportPlugin.getJenkinsNodeOnlineCount()));
            nodes.setExecutorTotalCount(printHistogram(supportPlugin.getJenkinsExecutorTotalCount()));
            nodes.setExecutorUsedCount(printHistogram(supportPlugin.getJenkinsExecutorUsedCount()));
        }


        nodes.setMasterNode(getMasterNodeInfo(jenkins));
        for (Node node : jenkins.getNodes()) {
            nodes.addNode(getNodeInfo(node));
        }

        return nodes;
    }

    private Nodes.Histogram printHistogram(Histogram histogram) {
        Nodes.Histogram h = new Nodes.Histogram();
        h.setSampleSize(histogram.getCount());
        Snapshot snapshot = histogram.getSnapshot();
        h.setMean(snapshot.getMean());
        h.setMedian(snapshot.getMedian());
        h.setStandardDeviation(snapshot.getStdDev());
        h.setMinimum(snapshot.getMin());
        h.setMaximum(snapshot.getMax());
        h.setNintyFifthPercentile(snapshot.get95thPercentile());
        h.setNintyNinthPercentile(snapshot.get99thPercentile());
        return h;
    }

    private Nodes.MasterNode getMasterNodeInfo(Jenkins jenkins) {
        Nodes.MasterNode mn = new Nodes.MasterNode();
        mn.setDescription(Util.fixNull(jenkins.getNodeDescription()));
        mn.setNumOfExecutors(jenkins.getNumExecutors());
        mn.setFsRoot(jenkins.getRootDir().getAbsolutePath());
        mn.setLabels(getLabelString(jenkins));
        mn.setMode(jenkins.getMode().getName());
        mn.setVersion(Launcher.VERSION);
        About.VersionDetails versionDetails = new GetJavaInfo().call();
        mn.setVersionDetails(versionDetails);
        return mn;
    }

    private Nodes.Node getNodeInfo(Node node) {
        Nodes.Node n = new Nodes.Node();
        n.setName(node.getNodeName());
        n.setDescriptor(SupportUtils.getDescriptorName(node));
        n.setDescription(node.getNodeDescription());
        n.setNumOfExecutors(node.getNumExecutors());
        FilePath rootPath = node.getRootPath();

        if (rootPath != null) {
            n.setFsRoot(rootPath.getRemote());
        } else if (node instanceof Slave) {
            n.setFsRoot(Slave.class.cast(node).getRemoteFS());
        }

        n.setLabels(getLabelString(node));
        n.setUsage(node.getMode().getName());

        if (node instanceof Slave) {
            Slave slave = (Slave) node;
            n.setLaunchMethod(SupportUtils.getDescriptorName(slave.getLauncher()));
            n.setAvailability(SupportUtils.getDescriptorName(slave.getRetentionStrategy()));
        }

        VirtualChannel channel = node.getChannel();
        if (channel == null) {
            n.setStatus("off-line");
        } else {
            n.setStatus("on-line");
            try {

                n.setVersion(AsyncResultCache.get(node, slaveVersionCache, new GetSlaveVersion(),
                                "slave.jar version", "(timeout with no cache available)"));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        "Could not get slave.jar version for " + node.getNodeName(), e);
            }
            try {
                n.setVersionDetails(AsyncResultCache.get(node, javaInfoCache,
                        new GetJavaInfo(), "Java info"));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not get Java info for " + node.getNodeName(), e);
            }
        }

        return n;
    }


    private static class GetSlaveVersion extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;

        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                value = {"NP_LOAD_OF_KNOWN_NULL_VALUE"},
                justification = "Findbugs mis-diagnosing closeQuietly's built-in null check"
        )
        public String call() throws RuntimeException {
            InputStream is = null;
            try {
                is = hudson.remoting.Channel.class.getResourceAsStream("/jenkins/remoting/jenkins-version.properties");
                if (is == null) {
                    return "N/A";
                }
                Properties properties = new Properties();
                try {
                    properties.load(is);
                    return properties.getProperty("version", "N/A");
                } catch (IOException e) {
                    return "N/A";
                }
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(NodesContent.class.getCanonicalName());
}