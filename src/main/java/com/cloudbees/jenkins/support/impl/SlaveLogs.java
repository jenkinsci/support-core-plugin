/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.cloudbees.jenkins.support.util.Helper;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.RingBufferLogHandler;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.cloudbees.jenkins.support.SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS;
import static com.cloudbees.jenkins.support.SupportPlugin.SUPPORT_DIRECTORY_NAME;
import static com.cloudbees.jenkins.support.impl.JenkinsLogs.LOG_FORMATTER;

/**
 * Adds the slave logs from all of the machines
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete.
public class SlaveLogs extends Component {
    private static final Logger LOGGER = Logger.getLogger(SlaveLogs.class.getCanonicalName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Slave Log Recorders";
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }



    @Override
    public void addContents(@NonNull Container container) {
        // expensive remote computation are pooled together and executed later concurrently across all the slaves
        List<java.util.concurrent.Callable<List<FileContent>>> tasks = Lists.newArrayList();
        SmartLogFetcher logFetcher = new SmartLogFetcher("cache", new LogFilenameFilter()); // id is awkward because of backward compatibility
        SmartLogFetcher winswLogFetcher = new SmartLogFetcher("winsw", new WinswLogfileFilter());
        final boolean needHack = SlaveLogFetcher.isRequired();

        for (final Node node : Helper.getActiveInstance().getNodes()) {
            if (node.toComputer() instanceof SlaveComputer) {
                container.add(
                        new PrintedContent("nodes/slave/" + node.getNodeName() + "/jenkins.log") {
                            @Override
                            protected void printTo(PrintWriter out) throws IOException {
                                Computer computer = node.toComputer();
                                if (computer == null) {
                                    out.println("N/A");
                                } else {
                                    try {
                                        List<LogRecord> records = null;
                                        if (needHack) {
                                            VirtualChannel channel = computer.getChannel();
                                            if (channel != null) {
                                                hudson.remoting.Future<List<LogRecord>> future = SlaveLogFetcher.getLogRecords(channel);
                                                records = future.get(REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                                            }
                                        }

                                        if (records == null) {
                                            records = computer.getLogRecords();
                                        }

                                        for (ListIterator<LogRecord> iterator = records.listIterator(records.size());
                                             iterator.hasPrevious(); ) {
                                            LogRecord logRecord = iterator.previous();
                                            out.print(LOG_FORMATTER.format(logRecord));
                                        }
                                    } catch (Throwable e) {
                                        out.println();
                                        out.print(SupportLogFormatter.printThrowable(e));
                                    }
                                }
                                out.flush();

                            }
                        }
                );
            }

            addSlaveJulLogRecords(container, tasks, node, logFetcher);
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
                            container.add(c);
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
     * Captures a "recent" (but still fairly large number of) j.u.l entries written on this slave.
     *
     * @see JenkinsLogs#addMasterJulLogRecords(Container)
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

    private static class SlaveLogFetcher implements hudson.remoting.Callable<List<LogRecord>, RuntimeException> {

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

        /** {@inheritDoc} */
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO: do we have to verify some role?
        }

        public static hudson.remoting.Future<List<LogRecord>> getLogRecords(@NonNull VirtualChannel channel) throws IOException {
            return channel.callAsync(new SlaveLogFetcher());
        }

        /**
         * @deprecated Please use getLogRecords(Channel) instead. This method is synchronous which could cause
         * the channel to block.
         */
        @Deprecated
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
