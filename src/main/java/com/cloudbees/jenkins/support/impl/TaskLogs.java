package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import hudson.triggers.SafeTimerTask;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static com.cloudbees.jenkins.support.impl.JenkinsLogs.ROTATED_LOGFILE_FILTER;

/**
 * Task Log files from the controller node.
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
        return "Controller Task Log Recorders";
    }

    @Override
    public boolean isSelectedByDefault() {
        return true;
    }

    @Override
    public void addContents(@NonNull Container result) {
        addControllerTasksLogs(result);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }

    /**
     * Grabs any files that look like log files directly under <code>$JENKINS_HOME/logs</code>, just in case
     * any of them are useful.
     * Does not add anything if Jenkins instance is unavailable.
     * Some plugins write log files here.
     */
    private void addControllerTasksLogs(Container result) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            File logsRoot = SafeTimerTask.getLogsRoot();
            for (File logs : new File[] {logsRoot, new File(logsRoot, "tasks")}) {
                File[] files = logs.listFiles(ROTATED_LOGFILE_FILTER);
                if (files != null) {
                    Arrays.sort(files);
                    long recently = System.currentTimeMillis() - FileListCapComponent.MAX_LOG_FILE_AGE_MS;
                    for (File f : files) {
                        if (f.lastModified() > recently) {
                            result.add(new FileContent("task-logs/{0}", new String[] {f.getName()}, f));
                        }
                    }
                }
            }
        }
    }

}
