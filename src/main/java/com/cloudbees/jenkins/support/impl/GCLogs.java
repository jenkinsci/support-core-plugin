package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.UnfilteredFileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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

    //TODO: Remove the JDK 8 part after the June 2022 LTS that drop support for JDK 8
    static final String GCLOGS_JRE_SWITCH = "-Xloggc:";
    static final String GCLOGS_JRE9_SWITCH = "-Xlog:gc";
    // We need (?:[a-zA-Z]\:\\)? to handle Windows disk drive 
    static final String GCLOGS_JRE9_LOCATION = ".*:file=[\"]?(?<location>(?:[a-zA-Z]:\\\\)?[^\":]*).*";
    static final String GCLOGS_ROTATION_SWITCH = "-XX:+UseGCLogFileRotation";
    static final String GCLOGS_JRE9_ROTATION_SWITCH = ".*filecount=0.*";

    private static final String GCLOGS_RETENTION_PROPERTY = GCLogs.class.getName() + ".retention";

    /**
     * How many days of garbage collector log files should be included in the bundle. 
     * By default {@code 5} days. Any value less or equals to {@code 0} disables the retention.
     */
    private static final Integer GCLOGS_RETENTION_DAYS = Integer.getInteger(GCLOGS_RETENTION_PROPERTY, 5);

    private static final String GCLOGS_BUNDLE_ROOT = "nodes/master/logs/gc/";

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
    public void addContents(@NonNull Container result) {
        LOGGER.fine("Trying to gather GC logs for support bundle");
        String gcLogFileLocation = getGcLogFileLocation();
        if (gcLogFileLocation == null) {
            LOGGER.config("No GC logging enabled, nothing about it will be retrieved for support bundle.");
            return;
        }

        if (isFileLocationParameterized(gcLogFileLocation) || isGcLogRotationConfigured()) {
            handleRotatedLogs(gcLogFileLocation, result);
        } else {
            File file = new File(gcLogFileLocation);
            if (!file.exists()) {
                LOGGER.warning("[Support Bundle] GC Logging apparently configured, " +
                        "but file '" + gcLogFileLocation + "' not found");
                return;
            }
            result.add(new UnfilteredFileContent(GCLOGS_BUNDLE_ROOT + "gc.log", file));
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    /**
     * Two cases:
     * <ul>
     * <li>The file name contains <code>%t</code>, <code>%p</code> or <code>%n</code> somewhere in the middle:
     * then we are simply going to replace those by <code>.*</code> to find associated logs and match files by regex.
     * This will match GC logs from the current JVM execution, or possibly previous ones.</li>
     * <li>or that feature is not used, then we simply match by "starts with"</li>
     * </ul>
     *
     * @param gcLogFileLocation the specified value within <code>-Xlog:gc.*:file=[filename]:.*</code> for JDK 9+ or after <code>-Xloggc:[filename]</code> for JDK 8 
     * @param result            the container where to add the found logs, if any.
     * @see https://bugs.openjdk.java.net/browse/JDK-7164841
     */
    private void handleRotatedLogs(@NonNull final String gcLogFileLocation, Container result) {
        File gcLogFile = new File(gcLogFileLocation);

        // always add .* in the end because this is where the numbering is going to happen
        String regex = gcLogFile.getName().replaceAll("%[pt]", ".*") + ".*";
        final Pattern gcLogFilesPattern = Pattern.compile(regex);

        File parentDirectory = gcLogFile.getParentFile();

        if (parentDirectory == null || !parentDirectory.exists()) {
            LOGGER.warning("[Support Bundle] " + parentDirectory + " does not exist, cannot collect gc logging files.");
            return;
        }

        File[] gcLogs = parentDirectory.listFiles((dir, name) -> gcLogFilesPattern.matcher(name).matches());
        if (gcLogs == null || gcLogs.length == 0) {
            LOGGER.warning("No GC logging files found, although the VM argument was found. This is probably a bug.");
            return;
        }

        LOGGER.finest("Found " + gcLogs.length + " matching files in " + parentDirectory.getAbsolutePath());
        for (File gcLog : gcLogs) {
            if (shouldConsiderFile(gcLog)) {
                LOGGER.finest("Adding '" + gcLog.getName() + "' file");
                result.add(new UnfilteredFileContent(GCLOGS_BUNDLE_ROOT + "{0}", new String[]{gcLog.getName()}, gcLog));
            }
        }
    }

    @CheckForNull
    public String getGcLogFileLocation() {

        String fileLocation;
        
        if(isJava8OrBelow()) {
            String gcLogSwitch = vmArgumentFinder.findVmArgument(GCLOGS_JRE_SWITCH);
            if (gcLogSwitch == null) {
                LOGGER.fine("No GC Logging switch found, cannot collect gc logging files.");
                fileLocation = null;
            } else {
                fileLocation = gcLogSwitch.substring(GCLOGS_JRE_SWITCH.length());
            }
        } else {
            String gcLogSwitch = vmArgumentFinder.findVmArgument(GCLOGS_JRE9_SWITCH);
            if (gcLogSwitch == null) {
                LOGGER.fine("No GC Logging switch found, cannot collect gc logging files.");
                fileLocation = null;
            } else {
                Matcher fileLocationMatcher = Pattern.compile(GCLOGS_JRE9_LOCATION).matcher(gcLogSwitch);
                if(fileLocationMatcher.matches()) {
                    fileLocation = fileLocationMatcher.group("location");
                } else {
                    LOGGER.fine("No GC Logging custom file location found.");
                    fileLocation = null;
                }
            }
        }
        return fileLocation;
    }
    
    public boolean isFileLocationParameterized(String fileLocation) {
        return Pattern.compile(".*%[tp].*").matcher(fileLocation).find();
    }

    /**
     * Returns if the file passed in should be considered following the retention  @{link GCLOGS_RETENTION_DAYS}
     * configured.
     * @param gcLog the file
     * @return true if the file should be considered
     */
    private boolean shouldConsiderFile(File gcLog) {
        return GCLOGS_RETENTION_DAYS <= 0 || 
                gcLog.lastModified() > (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(GCLOGS_RETENTION_DAYS));
    }

    public boolean isGcLogRotationConfigured() {
        if (isJava8OrBelow()) {
            return vmArgumentFinder.findVmArgument(GCLOGS_ROTATION_SWITCH) != null;
        } else {
            // JDK 9 and -Xlog:gc rotation is defaulted to 5 files and only disabled if filecount=0 is set
            String gcLogSwitch = vmArgumentFinder.findVmArgument(GCLOGS_JRE9_SWITCH);
            return gcLogSwitch != null && !Pattern.compile(GCLOGS_JRE9_ROTATION_SWITCH).matcher(gcLogSwitch).find();
        }
    }

    /**
     * Return if this instance if running Java 8 or a lower version
     * (Can be replaced by JavaUtils.isRunningWithJava8OrBelow() since 2.164.1)
     * @ See https://openjdk.java.net/jeps/223
     * @return true if running java 8 or an older version
     */
    private boolean isJava8OrBelow() {
        return System.getProperty("java.specification.version").startsWith("1.");
    }

    /**
     * Isolated code to make it testable
     */
    protected static class VmArgumentFinder {
        @CheckForNull
        public String findVmArgument(String argName) {
            return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith(argName)).findFirst().orElse(null);
        }
    }
}
