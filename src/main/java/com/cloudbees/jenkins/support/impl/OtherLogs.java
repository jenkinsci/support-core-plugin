package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.cloudbees.jenkins.support.impl.JenkinsLogs.ROTATED_LOGFILE_FILTER;

/**
 * Root Log files from the controller node.
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class OtherLogs extends Component {

    private static final Logger LOGGER = Logger.getLogger(OtherLogs.class.getName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Controller Other Log Recorders";
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public void addContents(@NonNull Container result) {
        addOtherControllerLogs(result);
    }

    /**
     * Grabs any files that look like log files directly under {@code $JENKINS_HOME}, just in case
     * any of them are useful.
     * Does not add anything if Jenkins instance is unavailable.
     * Some plugins write log files here.
     */
    private void addOtherControllerLogs(Container result) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            GCLogs gcLogsComponents = Jenkins.lookup(GCLogs.class);
            String gcLogsFileLocation = gcLogsComponents == null ? null : gcLogsComponents.getGcLogFileLocation();
            
            FileFilter fileFilter;
            if (gcLogsFileLocation == null) {
                fileFilter = ROTATED_LOGFILE_FILTER;
            } else {
                try {
                    // If GC logs are inside the Jenkins root directory, we need to filter them
                    if(Files.isSameFile(new File(gcLogsFileLocation).getParentFile().toPath(), jenkins.getRootDir().toPath())) {
                        final Pattern gcLogFilesPattern = 
                            gcLogsComponents.isFileLocationParameterized(gcLogsFileLocation) || gcLogsComponents.isGcLogRotationConfigured() ?
                                Pattern.compile("^" + new File(gcLogsFileLocation).getName().replaceAll("%[pt]", ".*") + ".*") :
                                Pattern.compile("^" + new File(gcLogsFileLocation).getName() + "$");
                        fileFilter = pathname -> ROTATED_LOGFILE_FILTER.accept(pathname)
                            && !gcLogFilesPattern.matcher(pathname.getName()).matches();
                    } else {
                        fileFilter = ROTATED_LOGFILE_FILTER;
                    }
                } catch (IOException e) {
                    LOGGER.fine("[Support Bundle] Could check if GC Logs file location '" + gcLogsFileLocation 
                        + "' is in Jenkins root directory");
                    fileFilter = ROTATED_LOGFILE_FILTER;
                }
            }
            
            File[] files = jenkins.getRootDir().listFiles(fileFilter);
            if (files != null) {
                for (File f : files) {
                    result.add(new FileContent("other-logs/{0}", new String[]{f.getName()}, f));
                }
            }
        }
    }
}
