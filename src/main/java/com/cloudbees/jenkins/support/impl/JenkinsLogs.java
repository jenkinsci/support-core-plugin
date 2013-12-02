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
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Log files from the different nodes
 *
 * @author Stephen Connolly
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class JenkinsLogs extends Component {

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
        super.start(context);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void addContents(@NonNull Container result) {
        final Formatter formatter = new SupportLogFormatter();
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
        for (Map.Entry<String, LogRecorder> entry : Jenkins.getInstance().getLog().logRecorders.entrySet()) {
            String name = entry.getKey();
            final LogRecorder recorder = entry.getValue();
            result.add(new PrintedContent("nodes/master/logs/custom/" + name + ".log") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    for (LogRecord logRecord : recorder.getLogRecords()) {
                        out.print(formatter.format(logRecord));
                    }
                    out.flush();
                }
            });
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
}
