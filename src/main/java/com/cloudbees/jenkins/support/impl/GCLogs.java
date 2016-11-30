package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * GC Logs Retriever.
 *
 * <p>Introspects the running VM for <code>-Xloggc:blah.log</code> option and so on to propose including those if
 * found</p>
 * <p>
 * NOTE: currently only tested on OpenJDK / HotSpot.
 * </p>
 */
@Extension(ordinal = 90.0) // probably big too, see JenkinsLogs
public class GCLogs extends Component {

    static final String GCLOGS_JRE_SWITCH = "-Xloggc:";

    static final String GCLOGS_ROTATION_SWITCH = "-XX:+UseGCLogFileRotation";

    private static final String GCLOGS_BUNDLE_ROOT = "/nodes/master/logs/gc/";

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

        if (isGcLogRotationConfigured()) {
            handleRotatedLogs(gcLogFileLocation, result);
        } else {
            File file = new File(gcLogFileLocation);
            if (!file.exists()) {
                LOGGER.warning("[Support Bundle] GC Logging apparently configured, " +
                        "but file '" + gcLogFileLocation + "' not found");
                return;
            }
            result.add(new FileContent(GCLOGS_BUNDLE_ROOT + "gc.log", file));
        }
    }

    /**
     * Two cases:
     * <ul>
     * <li>The file name contains <code>%t</code> or <code>%p</code> somewhere in the middle:
     * then we are simply going to replace those by <code>.*</code> to find associated logs and match files by regex.
     * This will match GC logs from the current JVM execution, or possibly previous ones.</li>
     * <li>or that feature is not used, then we simply match by "starts with"</li>
     * </ul>
     *
     * @param gcLogFileLocation the specified value after <code>-Xloggc:</code>
     * @param result            the container where to add the found logs, if any.
     * @see https://bugs.openjdk.java.net/browse/JDK-7164841
     */
    private void handleRotatedLogs(@Nonnull final String gcLogFileLocation, Container result) {
        // always add .* in the end because this is where the numbering is going to happen
        String regex = gcLogFileLocation.replaceAll("%[pt]", ".*") + ".*";
        final Pattern gcLogFilesPattern = Pattern.compile(regex);

        File parentDirectory = new File(gcLogFileLocation).getParentFile();

        if (parentDirectory == null || !parentDirectory.exists()) {
            LOGGER.warning("[Support Bundle] " + parentDirectory + " does not exist, cannot collect gc logging files.");
            return;
        }

        File[] gcLogs = parentDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return gcLogFilesPattern.matcher(dir + "/" + name).matches();
            }
        });
        if (gcLogs == null || gcLogs.length == 0) {
            LOGGER.warning("No GC logging files found, although the VM argument was found. This is probably a bug.");
            return;
        }

        LOGGER.finest("Found " + gcLogs.length + " matching files in " + parentDirectory.getAbsolutePath());
        for (File gcLog : gcLogs) {
            LOGGER.finest("Adding '" + gcLog.getName() + "' file");
            result.add(new FileContent(GCLOGS_BUNDLE_ROOT + gcLog.getName(), gcLog));
        }
    }

    @CheckForNull
    private String getGcLogFileLocation() {

        String gcLogSwitch = vmArgumentFinder.findVmArgument(GCLOGS_JRE_SWITCH);
        if (gcLogSwitch == null) {
            LOGGER.fine("No GC Logging switch found, cannot collect gc logging files.");
            return null;
        }
        return gcLogSwitch.substring(GCLOGS_JRE_SWITCH.length());
    }

    private boolean isGcLogRotationConfigured() {
        return vmArgumentFinder.findVmArgument(GCLOGS_ROTATION_SWITCH) != null;
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
