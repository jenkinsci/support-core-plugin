package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.WebAppMain;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Log files from the controller node only.
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class JenkinsLogs extends Component {

    private static final Logger LOGGER = Logger.getLogger(JenkinsLogs.class.getName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Controller Log Recorders";
    }

    @Override
    public void addContents(@NonNull Container result) {
        addControllerJulRingBuffer(result);
        addControllerJulLogRecords(result);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }

    /**
     * Adds {@link Jenkins#logRecords} (from core) into the support bundle.
     *
     * <p>
     * This is a small ring buffer that contains most recent log entries emitted from j.u.l logging.
     *
     * @see WebAppMain#installLogger()
     */
    private void addControllerJulRingBuffer(Container result) {
        result.add(new LogRecordContent("nodes/master/logs/jenkins.log") {
            @Override
            public Iterable<LogRecord> getLogRecords() {
                return Lists.reverse(new ArrayList<LogRecord>(Jenkins.logRecords));
            }
        });
    }

    /**
     * Adds j.u.l logging output that the support-core plugin captures.
     *
     * <p>
     * Compared to {@link #addControllerJulRingBuffer(Container)}, this one uses disk files,
     * so it remembers larger number of entries.
     */
    private void addControllerJulLogRecords(Container result) {
        // this file captures the most recent of those that are still kept around in memory.
        // this overlaps with Jenkins.logRecords, and also overlaps with what's written in files,
        // but added nonetheless just in case.
        //
        // should be ignorable.
        result.add(new LogRecordContent("nodes/master/logs/all_memory_buffer.log") {
            @Override
            public Iterable<LogRecord> getLogRecords() {
                return SupportPlugin.getInstance().getAllLogRecords();
            }
        });

        final File[] julLogFiles = SupportPlugin.getLogsDirectory().listFiles(new LogFilenameFilter());
        if (julLogFiles == null) {
            LOGGER.log(Level.WARNING, "Cannot add controller java.util.logging logs to the bundle. Cannot access log files");
            return;
        }

        // log records written to the disk
        for (File file : julLogFiles){
            result.add(new FileContent("nodes/master/logs/{0}", new String[]{file.getName()}, file));
        }
    }

    /**
     * Matches log files and their rotated names, such as "foo.log" or "foo.log.1"
     */
    protected static final FileFilter ROTATED_LOGFILE_FILTER = new FileFilter() {
        final Pattern pattern = Pattern.compile("^.*\\.log(\\.\\d+)?$");

        public boolean accept(File f) {
            return pattern.matcher(f.getName()).matches() && f.length()>0;
        }
    };
}
