package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.SupportContext;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Extension;
import hudson.WebAppMain;
import hudson.logging.LogRecorder;
import hudson.model.PeriodicWork;
import hudson.security.Permission;
import hudson.util.CopyOnWriteList;
import hudson.util.io.RewindableFileOutputStream;
import hudson.util.io.RewindableRotatingFileOutputStream;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;

/**
 * Log files from the master node only.
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class JenkinsLogs extends Component {

    private static final Logger LOGGER = Logger.getLogger(JenkinsLogs.class.getName());
    private static final int MAX_ROTATE_LOGS = Integer.getInteger(JenkinsLogs.class.getName() + ".MAX_ROTATE_LOGS", 9);
    private final Map<String,LogRecorder> logRecorders = Jenkins.getInstance().getLog().logRecorders;
    private final File customLogs = new File(getLogsRoot(), "custom");

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Master Log Recorders";
    }

    @Override
    public void start(@NonNull SupportContext context) {
        Logger.getLogger("").addHandler(new CustomHandler());
    }

    @Override
    public void addContents(@NonNull Container result) {
        addMasterJulRingBuffer(result);
        addMasterJulLogRecords(result);
        addOtherMasterLogs(result);
        addLogRecorders(result);
    }

    /**
     * Dumps the content of {@link LogRecorder}, which is the groups of loggers configured
     * by the user. The contents are also ring buffer and only remembers recent 256 or so entries.
     */
    private void addLogRecorders(Container result) {
        for (Map.Entry<String, LogRecorder> entry : logRecorders.entrySet()) {
            String name = entry.getKey();
            String entryName = "nodes/master/logs/custom/{0}.log"; // name to be filtered in the bundle
            File storedFile = new File(customLogs, name + ".log");
            if (storedFile.isFile()) {
                result.add(new FileContent(entryName, new String[]{name}, storedFile));
            } else {
                // Was not stored for some reason; fine, just load the memory buffer.
                final LogRecorder recorder = entry.getValue();
                result.add(new LogRecordContent(entryName, new String[]{name}) {
                    @Override
                    public Iterable<LogRecord> getLogRecords() {
                        return recorder.getLogRecords();
                    }
                });
            }
        }
    }

    /**
     * Grabs any files that look like log files directly under {@code $JENKINS_HOME}, just in case
     * any of them are useful.
     * Does not add anything if Jenkins instance is unavailable.
     * Some plugins write log files here.
     */
    private void addOtherMasterLogs(Container result) {
        final Jenkins jenkins = Jenkins.getInstance();
        File[] files = jenkins.getRootDir().listFiles(ROTATED_LOGFILE_FILTER);
        if (files != null) {
            for (File f : files) {
                result.add(new FileContent("other-logs/{0}", new String[]{f.getName()}, f));
            }
        }
        File logs = getLogsRoot();
        files = logs.listFiles(ROTATED_LOGFILE_FILTER);
        if (files != null) {
            for (File f : files) {
                result.add(new FileContent("other-logs/{0}", new String[]{f.getName()}, f));
            }
        }

        File taskLogs = new File(logs, "tasks");
        files = taskLogs.listFiles(ROTATED_LOGFILE_FILTER);
        if (files != null) {
            for (File f : files) {
                result.add(new FileContent("other-logs/{0}", new String[]{f.getName()}, f));
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
    private File getLogsRoot() {
        final String overriddenLogsRoot = System.getProperty("hudson.triggers.SafeTimerTask.logsTargetDir");
        if (overriddenLogsRoot == null) {
            return new File(Jenkins.get().getRootDir(), "logs");
        } else {
            return new File(overriddenLogsRoot);
        }
    }

    /**
     * Adds {@link Jenkins#logRecords} (from core) into the support bundle.
     *
     * <p>
     * This is a small ring buffer that contains most recent log entries emitted from j.u.l logging.
     *
     * @see WebAppMain#installLogger()
     */
    private void addMasterJulRingBuffer(Container result) {
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
     * Compared to {@link #addMasterJulRingBuffer(Container)}, this one uses disk files,
     * so it remembers larger number of entries.
     */
    private void addMasterJulLogRecords(Container result) {
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

        final File[] julLogFiles = SupportPlugin.getRootDirectory().listFiles(new LogFilenameFilter());
        if (julLogFiles == null) {
            LOGGER.log(Level.WARNING, "Cannot add master java.util.logging logs to the bundle. Cannot access log files");
            return;
        }

        // log records written to the disk
        for (File file : julLogFiles){
            result.add(new FileContent("nodes/master/logs/{0}", new String[]{file.getName()}, file));
        }
    }


    @SuppressWarnings(value="SIC_INNER_SHOULD_BE_STATIC_NEEDS_THIS", justification="customLogs is not static, so this is a bug in FB")
    private final class LogFile {
        private final RewindableRotatingFileOutputStream stream;
        private final Handler handler;
        private int count;
        @SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification="if mkdirs fails, will just get a stack trace later")
        LogFile(String name) throws IOException {
            customLogs.mkdirs();
            stream = new RewindableRotatingFileOutputStream(new File(customLogs, name + ".log"), MAX_ROTATE_LOGS);
            // TODO there is no way to avoid rotating when first opened; if .rewind is skipped, the file is just truncated
            stream.rewind();
            handler = new StreamHandler(stream, new SupportLogFormatter());
            handler.setLevel(Level.ALL);
            count = 0;
        }
        void publish(LogRecord record) throws IOException {
            boolean rewind = false;
            synchronized (this) {
                if (count++ > 9999) { // make sure it does not get enormous during a single session
                    count = 0;
                    rewind = true;
                }
            }
            if (rewind) {
                stream.rewind();
            }
            handler.publish(record);
            LogFlusher.scheduleFlush(handler);
        }
    }

    private final class CustomHandler extends Handler {

        private final Map<String,LogFile> logFiles = new HashMap<String,LogFile>();

        /** JENKINS-27669: try to preload classes that will be needed by {@link #publish} */
        CustomHandler() {
            Arrays.hashCode(new Class<?>[] {
                Map.Entry.class,
                LogRecorder.class,
                LogRecorder.Target.class,
                LogFile.class,
                RewindableFileOutputStream.class,
                RewindableRotatingFileOutputStream.class,
                StreamHandler.class,
                SupportLogFormatter.class,
                LogFlusher.class,
                CopyOnWriteList.class,
                PrintWriter.class,
                Throwable.class,
            });
        }

        @Override public void publish(LogRecord record) {
            for (Map.Entry<String,LogRecorder> entry : logRecorders.entrySet()) {
                for (LogRecorder.Target target : entry.getValue().targets) {
                    if (target.includes(record)) {
                        try {
                            String name = entry.getKey();
                            LogFile logFile;
                            synchronized (logFiles) {
                                logFile = logFiles.get(name);
                                if (logFile == null) {
                                    logFile = new LogFile(name);
                                    logFiles.put(name, logFile);
                                }
                            }
                            logFile.publish(record);
                        } catch (IOException x) {
                            x.printStackTrace(); // TODO probably unsafe to log this
                        }
                    }
                }
            }
        }

        @Override public void flush() {}

        @Override public void close() throws SecurityException {}

    }

    @Extension public static final class LogFlusher extends PeriodicWork {

        private static final Set<Handler> unflushedHandlers = new HashSet<Handler>();

        static synchronized void scheduleFlush(Handler h) {
            unflushedHandlers.add(h);
        }

        @Override public long getRecurrencePeriod() {
            return 3000; // 3s
        }

        @Override protected void doRun() throws Exception {
            Handler[] handlers;
            synchronized (LogFlusher.class) {
                handlers = unflushedHandlers.toArray(new Handler[unflushedHandlers.size()]);
                unflushedHandlers.clear();
            }
            for (Handler h : handlers) {
                h.flush();
            }
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

    protected static final Formatter LOG_FORMATTER = new SupportLogFormatter();
}
