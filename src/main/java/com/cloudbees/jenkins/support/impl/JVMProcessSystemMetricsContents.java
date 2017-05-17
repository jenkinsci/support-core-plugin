package com.cloudbees.jenkins.support.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * System metrics of the JVM process. Only supports Unix.
 */
@Extension
public class JVMProcessSystemMetricsContents extends ProcFilesRetriever {

    private static final Logger LOGGER = Logger.getLogger(JVMProcessSystemMetricsContents.class.getName());

    static final Map<String,String> UNIX_PROC_CONTENTS;
    static {
        UNIX_PROC_CONTENTS = new HashMap<String,String>();
        UNIX_PROC_CONTENTS.put("/proc/meminfo", "meminfo.txt");
        UNIX_PROC_CONTENTS.put("/proc/self/status", "self/status.txt");
        UNIX_PROC_CONTENTS.put("/proc/self/cmdline", "self/cmdline");
        UNIX_PROC_CONTENTS.put("/proc/self/environ", "self/environ");
        UNIX_PROC_CONTENTS.put("/proc/self/limits", "self/limits.txt");
        UNIX_PROC_CONTENTS.put("/proc/self/mountstats", "self/mountstats.txt");
    }

    @Override
    public Map<String, String> getFilesToRetrieve() {
        return UNIX_PROC_CONTENTS;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "JVM process system metrics (Linux only)";
    }
}
