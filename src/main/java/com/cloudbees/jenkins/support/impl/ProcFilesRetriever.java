package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FilePathContent;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Base class for gathering specified /proc files
 */
public abstract class ProcFilesRetriever extends ObjectComponent<Computer> {
    private static final Logger LOGGER = Logger.getLogger(ProcFilesRetriever.class.getName());
    private final WeakHashMap<Node, SystemPlatform> systemPlatformCache = new WeakHashMap<>();

    protected static String getNodeName(Node node) {
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }

    /**
     * Returns the map of files that should be retrieved.
     * <p>
     * <code>file name =&gt; path in the support bundle</code>.
     * </p>
     * <p>
     * For example <code>/proc/meminfo =&gt; meminfo.txt</code>.
     * </p>
     *
     * @return the map of files that should be retrieved and put in the support bundle.
     */
    abstract public Map<String, String> getFilesToRetrieve();

    protected List<Node> getNodes() {
        final Jenkins jenkins = Jenkins.get();
        final List<Node> agents = jenkins.getNodes();
        List<Node> allNodes = new ArrayList<>(agents.size() + 1);
        allNodes.add(jenkins);
        allNodes.addAll(agents);
        return allNodes;
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(@NonNull Container container) {
        for (Node node : getNodes()) {
            Optional.ofNullable(node.toComputer()).ifPresent(computer -> addContents(container, computer));
        }
    }

    @Override
    public void addContents(@NonNull Container container, Computer item) {
        Optional.ofNullable(item.getNode()).ifPresent(node -> addUnixContents(container, node));
    }

    protected void addUnixContents(@NonNull Container container, final @NonNull Node node) {
        Computer c = node.toComputer();
        if (c == null || c.isOffline()) {
            return;
        }
        // fast path bailout for Windows
        if (!Boolean.TRUE.equals(c.isUnix())) {
            return;
        }
        SystemPlatform nodeSystemPlatform = getSystemPlatform(node);
        if (!SystemPlatform.LINUX.equals(nodeSystemPlatform)) {
            return;
        }
        String name;
        if (node instanceof Jenkins) {
            name = "master";
        } else {
            name = "slave/" + node.getNodeName();
        }

        for (Map.Entry<String, String> procDescriptor : getFilesToRetrieve().entrySet()) {
            container.add(new FilePathContent("nodes/{0}/proc/{1}", new String[]{name, procDescriptor.getValue()},
                    new FilePath(c.getChannel(), procDescriptor.getKey())));
        }

        afterAddUnixContents(container, node, name);
    }

    /**
     * Override this method if you want to hook some code after {@link #addUnixContents(Container, Node)}.
     *
     * @param container the support {@link Container}.
     * @param node the node for which the method is called.
     * @param name the node name, <em>"master"</em> if Master, and <em>slave/${nodeName}</em> if an agent.
     */
    protected void afterAddUnixContents(@NonNull Container container, final @NonNull Node node, String name) {
    }

    public SystemPlatform getSystemPlatform(Node node) {
        try {
            return AsyncResultCache.get(node, systemPlatformCache, new SystemPlatform.GetCurrentPlatform(), "platform",
                                        SystemPlatform.UNKNOWN);
        } catch (IOException e) {
            final LogRecord record = new LogRecord(Level.FINE, "Could not retrieve system platform type from {0}");
            record.setParameters(new Object[]{getNodeName(node)});
            record.setThrown(e);
            LOGGER.log(record);
        }
        return SystemPlatform.UNKNOWN;
    }

    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Computer.class.isAssignableFrom(clazz);
    }
}
