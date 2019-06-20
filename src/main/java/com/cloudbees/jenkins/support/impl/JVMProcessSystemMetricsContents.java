package com.cloudbees.jenkins.support.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import jenkins.model.Jenkins;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * System metrics of the JVM process. Only supports Unix. We use the advanced retriever to specify which files will be
 * filtered.
 */
public abstract class JVMProcessSystemMetricsContents extends AdvancedProcFilesRetriever {
    @Extension
    public static class Master extends JVMProcessSystemMetricsContents {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Master JVM process system metrics (Linux only)";
        }

        @Override
        protected List<Node> getNodes() {
            return Collections.singletonList(Jenkins.get());
        }
    }

    @Extension
    public static class Agents extends JVMProcessSystemMetricsContents {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Agent JVM process system metrics (Linux only)";
        }

        @Override
        public boolean isSelectedByDefault() {
            return false;
        }

        @Override
        protected List<Node> getNodes() {
            return Jenkins.get().getNodes();
        }
    }

    static final Set<ProcFile> UNIX_PROC_CONTENTS;

    static {
        Set<ProcFile> contents = new HashSet<>();
        // These files don't need filtering
        contents.add(ProcFile.of("/proc/meminfo", "meminfo.txt", false));
        contents.add(ProcFile.of("/proc/self/status", "self/status.txt", false));
        contents.add(ProcFile.of("/proc/self/cmdline", "self/cmdline", false));
        // This one should be filtered
        contents.add(ProcFile.of("/proc/self/environ", "self/environ", true));
        // These files don't need filtering
        contents.add(ProcFile.of("/proc/self/limits", "self/limits.txt", false));
        contents.add(ProcFile.of("/proc/self/mountstats", "self/mountstats.txt", false));
        UNIX_PROC_CONTENTS = Collections.unmodifiableSet(contents);
    }


    @Override
    public Set<ProcFile> getProcFilesToRetrieve() {
        return UNIX_PROC_CONTENTS;
    }
}
