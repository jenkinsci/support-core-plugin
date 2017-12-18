package com.cloudbees.jenkins.support.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * System metrics of the JVM process. Only supports Unix.
 */
public abstract class JVMProcessSystemMetricsContents extends ProcFilesRetriever {
    @Extension
    public static class Master extends JVMProcessSystemMetricsContents {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Master JVM process system metrics (Linux only)";
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
    }

    static final Map<String, String> UNIX_PROC_CONTENTS;

    static {
        Map<String, String> contents = new HashMap<String, String>();
        contents.put("/proc/meminfo", "meminfo.txt");
        contents.put("/proc/self/status", "self/status.txt");
        contents.put("/proc/self/cmdline", "self/cmdline");
        contents.put("/proc/self/environ", "self/environ");
        contents.put("/proc/self/limits", "self/limits.txt");
        contents.put("/proc/self/mountstats", "self/mountstats.txt");
        UNIX_PROC_CONTENTS = Collections.unmodifiableMap(contents);
    }


    @Override
    public Map<String, String> getFilesToRetrieve() {
        return UNIX_PROC_CONTENTS;
    }
}
