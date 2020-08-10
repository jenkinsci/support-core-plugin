package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import static com.cloudbees.jenkins.support.impl.JenkinsLogs.ROTATED_LOGFILE_FILTER;

/**
 * Task Log files from the master node.
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class TaskLogs extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Master Task Log Recorders";
    }

    @Override
    public boolean isSelectedByDefault() {
        return true;
    }

    @Override
    public void addContents(@NonNull Container result) {
        addOtherMasterLogs(result);
    }
    
    /**
     * Grabs any files that look like log files directly under <code>$JENKINS_HOME/logs</code>, just in case
     * any of them are useful.
     * Does not add anything if Jenkins instance is unavailable.
     * Some plugins write log files here.
     */
    private void addOtherMasterLogs(Container result) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            File logs = getLogsRoot();
            File[] files = logs.listFiles(ROTATED_LOGFILE_FILTER);
            if (files != null) {
                for (File f : files) {
                    result.add(new FileContent("task-logs/{0}", new String[]{f.getName()}, f));
                }
            }

            File taskLogs = new File(logs, "tasks");
            files = taskLogs.listFiles(ROTATED_LOGFILE_FILTER);
            if (files != null) {
                for (File f : files) {
                    result.add(new FileContent("task-logs/{0}", new String[]{f.getName()}, f));
                }
            }
        }
    }

    /**
     * Returns the root directory for logs (historically always found under <code>$JENKINS_HOME/logs</code>.
     * Configurable since Jenkins 2.114.
     *
     * @see hudson.triggers.SafeTimerTask#LOGS_ROOT_PATH_PROPERTY
     * @return the root directory for logs.
     */
    protected static File getLogsRoot() {
        final String overriddenLogsRoot = System.getProperty("hudson.triggers.SafeTimerTask.logsTargetDir");
        if (overriddenLogsRoot == null) {
            return new File(Jenkins.get().getRootDir(), "logs");
        } else {
            return new File(overriddenLogsRoot);
        }
    }
}
