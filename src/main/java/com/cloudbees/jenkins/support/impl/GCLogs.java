package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

/**
 * GC Logs Retriever.
 * <p>
 * <p>Introspects the running VM for <code>-Xloggc:blah.log</code> option and so on to propose including those if
 * found</p>
 * <p>
 * NOTE: currently only tested on OpenJDK / HotSpot.
 * </p>
 */
@Extension(ordinal = 90.0) // probably big too, see JenkinsLogs
public class GCLogs extends Component {

    static final String GCLOG_JRE_SWITCH = "-Xloggc:";

    private static final Logger LOGGER = Logger.getLogger(GCLogs.class.getName());

    private final VmArgumentFinder vmArgumentFinder;

    public GCLogs() {
        this(new VmArgumentFinder());
    }

    /**
     * Designed for testing use only.
     *
     * @param vmArgumentFinder pass the impl you want to override the default one.
     */
    GCLogs(VmArgumentFinder vmArgumentFinder) {
        this.vmArgumentFinder = vmArgumentFinder;
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Garbage Collection Logs";
    }

    @Override
    public boolean isEnabled() {
        boolean gcLogsConfigured = getGcLogFileLocation() != null;
        LOGGER.fine("GC logs configured: " + gcLogsConfigured);
        return super.isEnabled() && gcLogsConfigured;
    }

    @Override
    public void addContents(@NonNull Container result) {
        LOGGER.fine("Trying to gather GC logs for support bundle");
        String gcLogFileLocation = getGcLogFileLocation();
        assert gcLogFileLocation != null; // non nullable here 'cause isEnabled() already checks it

        // TODO : log rotation improved logic

        File file = new File(gcLogFileLocation);
        if (!file.exists()) {
            LOGGER.warning("[Support Bundle] GC Logging apparently configured, " +
                    "but file '" + gcLogFileLocation + "' not found");
            return;
        }
        result.add(new FileContent("/nodes/master/logs/gc.log", file));
    }

    @CheckForNull
    private String getGcLogFileLocation() {

        String gcLogSwitch = vmArgumentFinder.findVmArgument(GCLOG_JRE_SWITCH);
        if (gcLogSwitch == null) {
            LOGGER.fine("No GC Logging switch found, no GC logs will be gathered.");
            return null;
        }
        return gcLogSwitch.substring(GCLOG_JRE_SWITCH.length());
    }

    /**
     * Isolated code to make it testable
     */
    static class VmArgumentFinder {
        @CheckForNull
        public String findVmArgument(String argName) {
            for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (argument.startsWith(argName)) {
                    return argument;
                }
            }
            return null;
        }
    }
}
