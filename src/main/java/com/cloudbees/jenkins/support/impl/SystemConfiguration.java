package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * System configuration data (CPU information, swap configuration, mount points,
 * kernel messages and entropy)
 */
@Extension
public class SystemConfiguration extends Component {

    private final WeakHashMap<Node,SystemPlatform> systemPlatformCache = new WeakHashMap<Node, SystemPlatform>();

    private final WeakHashMap<Node,String> sysCtlCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> userIdCache = new WeakHashMap<Node, String>();

    private static final Logger LOGGER = Logger.getLogger(SystemConfiguration.class.getName());

    static Map<String,String> UNIX_PROC_CONTENTS;
    static {
        UNIX_PROC_CONTENTS = new HashMap<String,String>();
        UNIX_PROC_CONTENTS.put("/proc/swaps", "swaps.txt");
        UNIX_PROC_CONTENTS.put("/proc/cpuinfo", "cpuinfo.txt");
        UNIX_PROC_CONTENTS.put("/proc/mounts", "mounts.txt");
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "System configuration (Linux only)";
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(@NonNull Container container) {
        Jenkins j = Jenkins.getInstance();
        addUnixContents(container, j);

        for (Node node : j.getNodes()) {
            addUnixContents(container, node);
        }
    }

    private void addUnixContents(@NonNull Container container, final @NonNull Node node) {
        Computer c = node.toComputer();
        if (c == null) {
            return;
        }
        // fast path bailout for Windows
        if (c instanceof SlaveComputer && !Boolean.TRUE.equals(((SlaveComputer) c).isUnix())) {
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

        for (Map.Entry<String, String> procDescriptor : UNIX_PROC_CONTENTS.entrySet()) {
            container.add(new FileContent("nodes/" + name + "/proc/" + procDescriptor.getValue(), new File(procDescriptor.getKey())));
        }

        container.add(CommandOutputContent.runOnNodeAndCache(sysCtlCache, node, "nodes/" + name + "/sysctl.txt", "/bin/sh", "-c", "sysctl -a"));
        container.add(CommandOutputContent.runOnNode(node, "nodes/" + name + "/dmesg.txt", "/bin/sh", "-c", "dmesg | tail -1000"));
        container.add(CommandOutputContent.runOnNodeAndCache(userIdCache, node, "nodes/" + name + "/userid.txt", "/bin/sh", "-c", "id -a"));
    }

    public SystemPlatform getSystemPlatform(Node node) {
        try {
            return AsyncResultCache.get(node, systemPlatformCache, new SystemPlatform.GetCurrentPlatform(), "platform", SystemPlatform.UNKNOWN);
        } catch (IOException e) {
            final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve system platform type from {0}");
            lr.setParameters(new Object[]{getNodeName(node)});
            lr.setThrown(e);
            LOGGER.log(lr);
        }
        return SystemPlatform.UNKNOWN;
    }

    private static String getNodeName(Node node) {
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }
}
