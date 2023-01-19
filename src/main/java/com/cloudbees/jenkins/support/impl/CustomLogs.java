package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.Terminator;
import hudson.logging.LogRecorder;
import hudson.model.PeriodicWork;
import hudson.security.Permission;
import hudson.triggers.SafeTimerTask;
import hudson.util.CopyOnWriteList;
import hudson.util.io.RewindableFileOutputStream;
import hudson.util.io.RewindableRotatingFileOutputStream;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * Custom Log recorders files from the controller.
 */
@Extension(ordinal = 100.0)
public class CustomLogs extends Component {

    private static final Logger LOGGER = Logger.getLogger(CustomLogs.class.getName());
    private static final int MAX_ROTATE_LOGS = Integer.getInteger(CustomLogs.class.getName() + ".MAX_ROTATE_LOGS", 9);
    private static final File customLogs = new File(SafeTimerTask.getLogsRoot(), "custom");
    private final List<LogRecorder> logRecorders = Jenkins.get().getLog().getRecorders();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Controller Custom Log Recorders";
    }

    @Override
    public void addContents(@NonNull Container result) {
        addLogRecorders(result);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }

    /**
     * Dumps the content of {@link LogRecorder}, which is the groups of loggers configured
     * by the user. The contents are also ring buffer and only remembers recent 256 or so entries.
     */
    private void addLogRecorders(Container result) {
        for (final LogRecorder recorder : logRecorders) {
            String name = recorder.getName();
            String entryName = "nodes/master/logs/custom/{0}.log"; // name to be filtered in the bundle
            File storedFile = new File(customLogs, name + ".log");
            if (storedFile.isFile()) {
                result.add(new FileContent(entryName, new String[]{name}, storedFile));
            } else {
                // Was not stored for some reason; fine, just load the memory buffer.
                result.add(new LogRecordContent(entryName, new String[]{name}) {
                    @Override
                    public Iterable<LogRecord> getLogRecords() {
                        return recorder.getLogRecords();
                    }
                });
            }
        }
    }

    private static final class LogFile {
        private final RewindableRotatingFileOutputStream stream;
        private final Handler handler;
        private int count;
        @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification="if mkdirs fails, will just get a stack trace later")
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

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.SYSTEM_CONFIG_LOADED)
    @Restricted(NoExternalUse.class)
    public void startCustomHandler() {
        Logger.getLogger("").addHandler(new CustomHandler());
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
            for (final LogRecorder recorder : logRecorders) {
                for (LogRecorder.Target target : recorder.getLoggers()) {
                    if (Boolean.TRUE.equals(target.matches(record))) {
                        String name = recorder.getName();
                        try {
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
                            LOGGER.warning("Error while publishing log records for '" + name);
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
            flush();
        }

        @Terminator public static void flush() {
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
}
