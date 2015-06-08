package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * System metrics of the JVM process. Only support Unix
 */
@Extension
public class JVMProcessSystemMetricsContents extends Component {

    private static final Logger LOGGER = Logger.getLogger(JVMProcessSystemMetricsContents.class.getName());

    private final WeakHashMap<Node,SystemPlatform> systemPlatformCache = new WeakHashMap<Node, SystemPlatform>();

    static Map<String,String> UNIX_PROC_CONTENTS;
    static {
        UNIX_PROC_CONTENTS = new HashMap<String,String>();
        UNIX_PROC_CONTENTS.put("/proc/meminfo", "meminfo.txt");
        UNIX_PROC_CONTENTS.put("/proc/self/status", "self/status.txt");
        UNIX_PROC_CONTENTS.put("/proc/self/cmdline", "self/cmdline");
        UNIX_PROC_CONTENTS.put("/proc/self/environ", "self/environ");
        UNIX_PROC_CONTENTS.put("/proc/self/limits", "self/limits.txt");
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "JVM process system metrics (Linux only)";
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

        for (Map.Entry<String,String> procDescriptor : UNIX_PROC_CONTENTS.entrySet()) {
            container.add(new FileContent("nodes/" + name + "/proc/" + procDescriptor.getValue(), new File(procDescriptor.getKey())));
        }
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
