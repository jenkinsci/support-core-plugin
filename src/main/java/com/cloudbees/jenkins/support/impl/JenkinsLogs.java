package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.FilePathContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.logging.LogRecorder;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import hudson.util.RingBufferLogHandler;
import hudson.util.io.ReopenableFileOutputStream;
import hudson.util.io.ReopenableRotatingFileOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
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

    private final Formatter formatter = new SupportLogFormatter();
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
        result.add(new PrintedContent("nodes/master/logs/jenkins.log") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                List<LogRecord> records = new ArrayList<LogRecord>(Jenkins.logRecords);
                for (ListIterator<LogRecord> iterator = records.listIterator(records.size());
                     iterator.hasPrevious(); ) {
                    LogRecord logRecord = iterator.previous();
                    out.print(formatter.format(logRecord));
                }
                out.flush();
            }
        });
        result.add(new PrintedContent("nodes/master/logs/all_memory_buffer.log") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                for (LogRecord logRecord : SupportPlugin.getInstance().getAllLogRecords()) {
                    out.print(formatter.format(logRecord));
                }
                out.flush();
            }
        });
        for (File f : Jenkins.getInstance().getRootDir().listFiles(new FilenameFilter() {
            final Pattern pattern = Pattern.compile("^.*\\.log(\\.\\d+)?$");

            public boolean accept(File dir, String name) {
                return pattern.matcher(name).matches();
            }
        })) {
            result.add(new FileContent("other-logs/" + f.getName(), f));
        }
        for (File file : new File(Jenkins.getInstance().getRootDir(), "support")
                .listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".log");
                    }
                })) {
            result.add(new FileContent("nodes/master/logs/" + file.getName(), file));
        }
        for (Map.Entry<String, LogRecorder> entry : logRecorders.entrySet()) {
            String name = entry.getKey();
            String entryName = "nodes/master/logs/custom/" + name + ".log";
            File storedFile = new File(customLogs, name + ".log");
            if (storedFile.isFile()) {
                result.add(new FileContent(entryName, storedFile));
            } else {
                // Was not stored for some reason; fine, just load the memory buffer.
                final LogRecorder recorder = entry.getValue();
                result.add(new PrintedContent(entryName) {
                    @Override
                    protected void printTo(PrintWriter out) throws IOException {
                        for (LogRecord logRecord : recorder.getLogRecords()) {
                            out.print(formatter.format(logRecord));
                        }
                        out.flush();
                    }
                });
            }
        }

        final boolean needHack = SlaveLogFetcher.isRequired();
        for (final Node node : Jenkins.getInstance().getNodes()) {
            if (node.toComputer() instanceof SlaveComputer) {
                result.add(
                        new PrintedContent("nodes/slave/" + node.getDisplayName() + "/jenkins.log") {
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
                                            out.print(formatter.format(logRecord));
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
            try {
                FilePath rootPath = node.getRootPath();
                if (rootPath != null) {
                    FilePath supportDir = rootPath.child("support");
                    if (supportDir.isDirectory()) {
                        for (FilePath file : supportDir.list("*.log")) {
                            result.add(
                                    new FilePathContent(
                                            "nodes/slave/" + node.getDisplayName() + "/logs/" + file.getName(),
                                            file));
                        }
                    }
                }
            } catch (IOException e) {
                // ignore
            } catch (InterruptedException e) {
                // ignore
            } catch (Throwable t) {
                // ignore
            }
            result.add(new PrintedContent("nodes/slave/" + node.getDisplayName() + "/logs/all_memory_buffer.log") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    try {
                        for (LogRecord logRecord : SupportPlugin.getInstance().getAllLogRecords(node)) {
                            out.print(formatter.format(logRecord));
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace(out);
                    }
                    out.flush();
                }
            });
            result.add(
                    new PrintedContent("nodes/slave/" + node.getDisplayName() + "/launch.log") {
                        @Override
                        protected void printTo(PrintWriter out) throws IOException {
                            Computer computer = node.toComputer();
                            if (computer != null) {
                                out.println(computer.getLog());
                            }
                            out.flush();
                        }
                    }
            );
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
            handler = new StreamHandler(stream, formatter);
            handler.setLevel(Level.ALL);
            count = 0;
        }
        synchronized void publish(LogRecord record) throws IOException {
            if (count++ > 9999) { // make sure it does not get enormous during a single session
                stream.rewind();
                count = 0;
            }
            handler.publish(record);
            handler.flush();
        }
    }

    private final class CustomHandler extends Handler {

        private final Map<String,LogFile> logFiles = new HashMap<String,LogFile>();

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

}
