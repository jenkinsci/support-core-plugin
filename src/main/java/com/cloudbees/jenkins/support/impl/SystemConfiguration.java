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
import com.cloudbees.jenkins.support.api.CommandOutputContent;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.StringContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Node;
import jenkins.security.MasterToSlaveCallable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System configuration data (CPU information, swap configuration, mount points,
 * kernel messages and entropy)
 */
public abstract class SystemConfiguration extends ProcFilesRetriever {
    @Extension
    public static class Master extends SystemConfiguration {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Master system configuration (Linux only)";
        }
    }

    @Extension
    public static class Agents extends SystemConfiguration {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Agent system configuration (Linux only)";
        }

        @Override
        public boolean isSelectedByDefault() {
            return false;
        }
    }

    private final WeakHashMap<Node,String> sysCtlCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> userIdCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> dmiCache = new WeakHashMap<Node, String>();

    private static final Logger LOGGER = Logger.getLogger(SystemConfiguration.class.getName());
    private static final Map<String,String>  UNIX_PROC_CONTENTS;

    static {
        Map<String, String> contents = new HashMap<String,String>();
        contents.put("/proc/swaps", "swaps.txt");
        contents.put("/proc/cpuinfo", "cpuinfo.txt");
        contents.put("/proc/mounts", "mounts.txt");
        contents.put("/proc/uptime", "system-uptime.txt");
        contents.put("/proc/net/rpc/nfs", "net/rpc/nfs.txt");
        contents.put("/proc/net/rpc/nfsd", "net/rpc/nfsd.txt");
        UNIX_PROC_CONTENTS = Collections.unmodifiableMap(contents);
    }

    @Override
    public Map<String, String> getFilesToRetrieve() {
        return UNIX_PROC_CONTENTS;
    }

    @Override
    protected void afterAddUnixContents(@NonNull Container container, final @NonNull Node node, String name, boolean shouldAnonymize) {
        container.add(
                CommandOutputContent.runOnNodeAndCache(sysCtlCache, node, shouldAnonymize, "nodes/" + name + "/sysctl.txt", "/bin/sh", "-c", "sysctl -a"));
        container.add(CommandOutputContent.runOnNode(node, shouldAnonymize, "nodes/" + name + "/dmesg.txt", "/bin/sh", "-c", "(dmesg --ctime 2>/dev/null||dmesg) |tail -1000"));
        container.add(CommandOutputContent.runOnNodeAndCache(userIdCache, node, shouldAnonymize, "nodes/" + name + "/userid.txt", "/bin/sh", "-c", "id -a"));
        container.add(new StringContent("nodes/" + name + "/dmi.txt", getDmiInfo(node, shouldAnonymize), shouldAnonymize));
    }

    public String getDmiInfo(Node node, boolean shouldAnonymize) {
        try {
            return AsyncResultCache.get(node, dmiCache, shouldAnonymize, new GetDmiInfo(), "dmi", "");
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not retrieve dmi content from " + getNodeName(node, shouldAnonymize), e);
        }
        return "no dmi info";
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings({"DMI_HARDCODED_ABSOLUTE_FILENAME"})
    static public class GetDmiInfo extends MasterToSlaveCallable<String, Exception> {
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
}
