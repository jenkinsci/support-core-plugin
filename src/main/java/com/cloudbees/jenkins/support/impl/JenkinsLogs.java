package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.SupportPlugin;
import static com.cloudbees.jenkins.support.SupportPlugin.*;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportContext;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.WebAppMain;
import hudson.logging.LogRecorder;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.slaves.SlaveComputer;
import hudson.util.CopyOnWriteList;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.RingBufferLogHandler;
import hudson.util.io.ReopenableFileOutputStream;
import hudson.util.io.ReopenableRotatingFileOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

/**
 * Log files from the different nodes
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class JenkinsLogs extends Component {

    private static final Logger LOGGER = Logger.getLogger(JenkinsLogs.class.getName());
    private final Map<String,LogRecorder> logRecorders = Jenkins.getInstance().getLog().logRecorders;
    private final File customLogs = new File(new File(Jenkins.getInstance().getRootDir(), "logs"), "custom");

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Log Recorders";
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
        addSlaveLaunchLog(result);

        SmartLogFetcher logFetcher = new SmartLogFetcher("cache", new LogFilenameFilter()); // id is awkward because of backward compatibility
        SmartLogFetcher winswLogFetcher = new SmartLogFetcher("winsw", new WinswLogfileFilter());
        final boolean needHack = SlaveLogFetcher.isRequired();

        // expensive remote computation are pooled together and executed later concurrently across all the slaves
        List<java.util.concurrent.Callable<List<FileContent>>> tasks = Lists.newArrayList();

        for (final Node node : Jenkins.getInstance().getNodes()) {
            if (node.toComputer() instanceof SlaveComputer) {
                result.add(
                        new PrintedContent("nodes/slave/" + node.getNodeName() + "/jenkins.log") {
                            @Override
                            protected void printTo(PrintWriter out) throws IOException {
                                Computer computer = node.toComputer();
                                if (computer == null) {
                                    out.println("N/A");
                                } else {
                                    try {
                                        List<LogRecord> records = needHack
                                                ? SlaveLogFetcher.getLogRecords(computer)
                                                : computer.getLogRecords();
                                        for (ListIterator<LogRecord> iterator = records.listIterator(records.size());
                                             iterator.hasPrevious(); ) {
                                            LogRecord logRecord = iterator.previous();
                                            out.print(LOG_FORMATTER.format(logRecord));
                                        }
                                    } catch (Throwable e) {
                                        out.println();
                                        e.printStackTrace(out);
                                    }
                                }
                                out.flush();

                            }
                        }
                );
            }
            addSlaveJulLogRecords(result, tasks, node, logFetcher);
            addWinsStdoutStderrLog(tasks, node, winswLogFetcher);
        }

        // execute all the expensive computations in parallel to speed up the time
        if (!tasks.isEmpty()) {
            ExecutorService service = Executors.newFixedThreadPool(
                    Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, tasks.size())),
                    new ExceptionCatchingThreadFactory(new DaemonThreadFactory())
            );
            try {
                long expiresNanoTime =
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC);
                for (java.util.concurrent.Future<List<FileContent>> r : service
                        .invokeAll(tasks, SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC,
                                TimeUnit.SECONDS)) {
                    try {
                        for (FileContent c : r
                                .get(Math.max(1, expiresNanoTime - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                            result.add(c);
                        }
                    } catch (ExecutionException e) {
                        LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
                    } catch (TimeoutException e) {
                        LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
                        r.cancel(false);
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
            } finally {
                service.shutdown();
            }
        }
    }

    /**
     * Adds slave launch logs, which captures the current and past running connections to the slave.
     *
     * <p>
     * In the presence of {@link Cloud} plugins like EC2, we want to find past slaves, not just current ones.
     * So we don't try to loop through {@link Node} here but just try to look at the file systems to find them
     * all.
     *
     * <p>
     * Generally these cloud plugins do not clean up old logs, so if run for a long time, the log directory
     * will be full of old files that are not very interesting. Use some heuristics to cut off logs
     * that are old.
     */
    private void addSlaveLaunchLog(Container result) {
        class Slave implements Comparable<Slave> {
            /**
             * Launch log directory of the slave: logs/slaves/NAME
             */
            File dir;
            long time;

            Slave(File dir, File lastLog) {
                this.dir = dir;
                this.time = lastLog.lastModified();
            }

            /** Slave name */
            String getName() { return dir.getName(); }

            /**
             * Use the primary log file's timestamp to compare newer slaves from older slaves.
             *
             * sort in descending order; newer ones first.
             */
            public int compareTo(Slave that) {
                long lhs = this.time;
                long rhs = that.time;
                if (lhs<rhs)    return 1;
                if (lhs>rhs)    return -1;
                return 0;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Slave slave = (Slave) o;

                if (time != slave.time) return false;

                return true;
            }

            @Override
            public int hashCode() {
                return (int) (time ^ (time >>> 32));
            }

            /**
             * If the file is more than a year old, can't imagine how that'd be of any interest.
             */
            public boolean isTooOld() {
                return time < System.currentTimeMillis()-TimeUnit.DAYS.toMillis(365);
            }
        }

        List<Slave> all = new ArrayList<Slave>();

        {// find all the slave launch log files and sort them newer ones first

            File slaveLogsDir = new File(Jenkins.getInstance().getRootDir(), "logs/slaves");
            File[] logs = slaveLogsDir.listFiles();
            if (logs!=null) {
                for (File dir : logs) {
                    File lastLog = new File(dir, "slave.log");
                    if (lastLog.exists()) {
                        Slave s = new Slave(dir, lastLog);
                        if (s.isTooOld()) continue;   // we don't care
                        all.add(s);
                    }
                }
            }

            Collections.sort(all);
        }

        {// this might be still too many, so try to cap them.
            int acceptableSize = Math.max(256, Jenkins.getInstance().getNodes().size() * 5);

            if (all.size() > acceptableSize)
                all = all.subList(0, acceptableSize);
        }

        // now add them all
        for (Slave s : all) {
            File[] files = s.dir.listFiles(ROTATED_LOGFILE_FILTER);
            if (files!=null)
                for (File f : files) {
                    result.add(new FileContent("nodes/slave/" + s.getName() + "/launchLogs/"+f.getName() , f));
                }
        }
    }

    /**
     * Captures a "recent" (but still fairly large number of) j.u.l entries written on this slave.
     *
     * @see #addMasterJulLogRecords(Container)
     */
    private void addSlaveJulLogRecords(Container result, List<java.util.concurrent.Callable<List<FileContent>>> tasks, final Node node, final SmartLogFetcher logFetcher) {
        final FilePath rootPath = node.getRootPath();
        if (rootPath != null) {
            // rotated log files stored on the disk
            tasks.add(new java.util.concurrent.Callable<List<FileContent>>(){
                public List<FileContent> call() throws Exception {
                    List<FileContent> result = new ArrayList<FileContent>();
                    FilePath supportPath = rootPath.child(SUPPORT_DIRECTORY_NAME);
                    if (supportPath.isDirectory()) {
                        final Map<String, File> logFiles = logFetcher.forNode(node).getLogFiles(supportPath);
                        for (Map.Entry<String, File> entry : logFiles.entrySet()) {
                            result.add(new FileContent(
                                            "nodes/slave/" + node.getNodeName() + "/logs/" + entry.getKey(),
                                            entry.getValue())
                            );
                        }
                    }
                    return result;
                }
            });
        }

        // this file captures the most recent of those that are still kept around in memory.
        // this overlaps with Jenkins.logRecords, and also overlaps with what's written in files,
        // but added nonetheless just in case.
        //
        // should be ignorable.
        result.add(new LogRecordContent("nodes/slave/" + node.getNodeName() + "/logs/all_memory_buffer.log") {
            @Override
            public Iterable<LogRecord> getLogRecords() throws IOException {
                try {
                    return SupportPlugin.getInstance().getAllLogRecords(node);
                } catch (InterruptedException e) {
                    throw (IOException)new InterruptedIOException().initCause(e);
                }
            }
        });
    }

    /**
     * Captures stdout/stderr log files produced by winsw.
     */
    private void addWinsStdoutStderrLog(List<java.util.concurrent.Callable<List<FileContent>>> tasks, final Node node, final SmartLogFetcher logFetcher) {
        final FilePath rootPath = node.getRootPath();
        if (rootPath != null) {
            // rotated log files stored on the disk
            tasks.add(new java.util.concurrent.Callable<List<FileContent>>(){
                public List<FileContent> call() throws Exception {
                    List<FileContent> result = new ArrayList<FileContent>();
                    final Map<String, File> logFiles = logFetcher.forNode(node).getLogFiles(rootPath);
                    for (Map.Entry<String, File> entry : logFiles.entrySet()) {
                        result.add(new FileContent(
                                        "nodes/slave/" + node.getNodeName() + "/logs/winsw/" + entry.getKey(),
                                        entry.getValue(), FileListCapComponent.MAX_FILE_SIZE)
                        );
                    }
                    return result;
                }
            });
        }
    }

    /**
     * Dumps the content of {@link LogRecorder}, which is the groups of loggers configured
     * by the user. The contents are also ring buffer and only remembers recent 256 or so entries.
     */
    private void addLogRecorders(Container result) {
        for (Map.Entry<String, LogRecorder> entry : logRecorders.entrySet()) {
            String name = entry.getKey();
            String entryName = "nodes/master/logs/custom/" + name + ".log";
            File storedFile = new File(customLogs, name + ".log");
            if (storedFile.isFile()) {
                result.add(new FileContent(entryName, storedFile));
            } else {
                // Was not stored for some reason; fine, just load the memory buffer.
                final LogRecorder recorder = entry.getValue();
                result.add(new LogRecordContent(entryName) {
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
     *
     * Some plugins write log files here.
     */
    private void addOtherMasterLogs(Container result) {
        for (File f : Jenkins.getInstance().getRootDir().listFiles(ROTATED_LOGFILE_FILTER)) {
            result.add(new FileContent("other-logs/" + f.getName(), f));
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

        // log records written to the disk
        for (File file : SupportPlugin.getRootDirectory().listFiles(new LogFilenameFilter())){
            result.add(new FileContent("nodes/master/logs/" + file.getName(), file));
        }
    }

    private static class SlaveLogFetcher implements Callable<List<LogRecord>, RuntimeException> {

        public static boolean isRequired() {
            try {
                SlaveComputer.class.getClassLoader().loadClass(SlaveComputer.class.getName() + "$SlaveLogFetcher");
                return false;
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        public List<LogRecord> call() throws RuntimeException {
            try {
                Class<?> aClass =
                        SlaveComputer.class.getClassLoader().loadClass(SlaveComputer.class.getName() + "$LogHolder");
                Field logHandler = aClass.getDeclaredField("SLAVE_LOG_HANDLER");
                boolean accessible = logHandler.isAccessible();
                try {
                    if (!accessible) {
                        logHandler.setAccessible(true);
                    }
                    Object instance = logHandler.get(null);
                    if (instance instanceof RingBufferLogHandler) {
                        RingBufferLogHandler handler = (RingBufferLogHandler) instance;
                        return new ArrayList<LogRecord>(handler.getView());
                    }
                } finally {
                    if (!accessible) {
                        logHandler.setAccessible(accessible);
                    }
                }
                throw new RuntimeException("Could not retrieve logs");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public static List<LogRecord> getLogRecords(Computer computer) throws IOException, InterruptedException {
            VirtualChannel channel = computer.getChannel();
            if (channel == null) {
                return Collections.emptyList();
            } else {
                return channel.call(new SlaveLogFetcher());
            }
        }
    }

    @SuppressWarnings(value="SIC_INNER_SHOULD_BE_STATIC_NEEDS_THIS", justification="customLogs is not static, so this is a bug in FB")
    private final class LogFile {
        private final ReopenableFileOutputStream stream;
        private final Handler handler;
        private int count;
        @SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification="if mkdirs fails, will just get a stack trace later")
        LogFile(String name) throws IOException {
            customLogs.mkdirs();
            stream = new ReopenableRotatingFileOutputStream(new File(customLogs, name + ".log"), 9);
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
                ReopenableFileOutputStream.class,
                ReopenableRotatingFileOutputStream.class,
                StreamHandler.class,
                SupportLogFormatter.class,
                LogFlusher.class,
                CopyOnWriteList.class,
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
    private static final FileFilter ROTATED_LOGFILE_FILTER = new FileFilter() {
        final Pattern pattern = Pattern.compile("^.*\\.log(\\.\\d+)?$");

        public boolean accept(File f) {
            return pattern.matcher(f.getName()).matches() && f.length()>0;
        }
    };

    private static final Formatter LOG_FORMATTER = new SupportLogFormatter();
}
