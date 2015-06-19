/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.Callable;
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

    private final WeakHashMap<Node,String> dmiCache = new WeakHashMap<Node, String>();

    private static final Logger LOGGER = Logger.getLogger(SystemConfiguration.class.getName());

    static Map<String,String> UNIX_PROC_CONTENTS;
    static {
        UNIX_PROC_CONTENTS = new HashMap<String,String>();
        UNIX_PROC_CONTENTS.put("/proc/swaps", "swaps.txt");
        UNIX_PROC_CONTENTS.put("/proc/cpuinfo", "cpuinfo.txt");
        UNIX_PROC_CONTENTS.put("/proc/mounts", "mounts.txt");
        UNIX_PROC_CONTENTS.put("/proc/uptime", "system-uptime.txt");
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
        container.add(new StringContent( "nodes/" + name + "/dmi.txt", getDmiInfo(node)));
    }

    public SystemPlatform getSystemPlatform(Node node) {
        try {
            return AsyncResultCache.get(node, systemPlatformCache, new SystemPlatform.GetCurrentPlatform(), "platform", SystemPlatform.UNKNOWN);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not retrieve command content from " + getNodeName(node), e);
        }
        return SystemPlatform.UNKNOWN;
    }

    public String getDmiInfo(Node node) {
        try {
            return AsyncResultCache.get(node, dmiCache, new GetDmiInfo(), "dmi", "");
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not retrieve dmi content from " + getNodeName(node), e);
        }
        return "no dmi info";
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings({"DMI_HARDCODED_ABSOLUTE_FILENAME"})
    static public class GetDmiInfo implements Callable<String, Exception> {
        private static final long serialVersionUID = 1L;
        public String call() {
            StringBuilder sb = new StringBuilder();

            File[] files = new File("/sys/devices/virtual/dmi/id").listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.canRead() && !file.isDirectory()) {
                        sb.append(file.getName());
                        sb.append(": ");
                        try {
                            sb.append(Util.loadFile(file).trim());
                        } catch (IOException e) {
                            sb.append("failed, " + e.getMessage());
                        }
                        sb.append('\n');
                    }
                }
            }
            return sb.toString();
        }
    }

    private static String getNodeName(Node node) {
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }
}
