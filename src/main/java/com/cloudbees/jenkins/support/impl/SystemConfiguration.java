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
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import com.cloudbees.jenkins.support.api.UnfilteredCommandOutputContent;
import com.cloudbees.jenkins.support.api.UnfilteredStringContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Node;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System configuration data (CPU information, swap configuration, mount points,
 * kernel messages and entropy)
 */
public abstract class SystemConfiguration extends AdvancedProcFilesRetriever {
    @Extension
    public static class Master extends SystemConfiguration {

        @DataBoundConstructor
        public Master() {
            super();
        }
        
        @Override
        @NonNull
        public String getDisplayName() {
            return "Master system configuration (Linux only)";
        }

        @Override
        protected List<Node> getNodes() {
            return Collections.singletonList(Jenkins.get());
        }

        @Override
        public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
            return Jenkins.class.isAssignableFrom(clazz);
        }

        @Override
        public boolean isApplicable(Computer item) {
            return item == Jenkins.get().toComputer();
        }
        
        @Override
        public DescriptorImpl getDescriptor() {
            return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
        }

        @Extension
        @Symbol("masterSystemConfigurationComponent")
        public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return "Master system configuration (Linux only)";
            }

        }
    }

    @Extension
    public static class Agents extends SystemConfiguration {

        @DataBoundConstructor
        public Agents() {
            super();
        }
        
        @Override
        @NonNull
        public String getDisplayName() {
            return "Agent system configuration (Linux only)";
        }

        @Override
        public boolean isSelectedByDefault() {
            return false;
        }

        @Override
        protected List<Node> getNodes() {
            return Jenkins.get().getNodes();
        }

        @Override
        public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
            return Jenkins.class.isAssignableFrom(clazz) || Computer.class.isAssignableFrom(clazz);
        }

        @Override
        public boolean isApplicable(Computer item) {
            return item != Jenkins.get().toComputer();
        }

        @Override
        public DescriptorImpl getDescriptor() {
            return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
        }

        @Extension
        @Symbol("agentSystemConfigurationComponent")
        public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return "Agent system configuration (Linux only)";
            }

        }
    }

    private final WeakHashMap<Node,String> sysCtlCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> userIdCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> dmiCache = new WeakHashMap<Node, String>();

    private static final Logger LOGGER = Logger.getLogger(SystemConfiguration.class.getName());
    private static final Set<ProcFile> UNIX_PROC_CONTENTS;

    static {
        Set<ProcFile> contents = new HashSet<>();

        contents.add(ProcFile.of("/proc/swaps", "swaps.txt", false));
        contents.add(ProcFile.of("/proc/cpuinfo", "cpuinfo.txt", false));
        contents.add(ProcFile.of("/proc/mounts", "mounts.txt", false));
        contents.add(ProcFile.of("/proc/uptime", "system-uptime.txt", false));
        contents.add(ProcFile.of("/proc/net/rpc/nfs", "net/rpc/nfs.txt",false));
        contents.add(ProcFile.of("/proc/net/rpc/nfsd", "net/rpc/nfsd.txt", false));
        UNIX_PROC_CONTENTS = Collections.unmodifiableSet(contents);
    }

    @Override
    public Set<ProcFile> getProcFilesToRetrieve() {
        return UNIX_PROC_CONTENTS;
    }

    @Override
    protected void afterAddUnixContents(@NonNull Container container, final @NonNull Node node, String name) {
        container.add(
                UnfilteredCommandOutputContent.runOnNodeAndCache(sysCtlCache, node, "nodes/{0}/sysctl.txt", new String[]{name},  "/bin/sh", "-c", "sysctl -a"));
        container.add(UnfilteredCommandOutputContent.runOnNode(node, "nodes/{0}/dmesg.txt", new String[]{name}, "/bin/sh", "-c", "(dmesg --ctime 2>/dev/null||dmesg) |tail -1000"));
        container.add(CommandOutputContent.runOnNodeAndCache(userIdCache, node, "nodes/{0}/userid.txt", new String[]{name}, "/bin/sh", "-c", "id -a"));
        container.add(new UnfilteredStringContent("nodes/{0}/dmi.txt", new String[]{name}, getDmiInfo(node)));
    }

    public String getDmiInfo(Node node) {
        try {
            return AsyncResultCache.get(node, dmiCache, new GetDmiInfo(), "dmi", "");
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not retrieve dmi content from " + getNodeName(node), e);
        }
        return "no dmi info";
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
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
