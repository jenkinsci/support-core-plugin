package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import com.cloudbees.jenkins.support.api.StringContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.timer.FileListCap;
import com.cloudbees.jenkins.support.util.CallAsyncWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import java.io.*;
import java.lang.management.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import jenkins.util.Timer;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Thread dumps from the nodes.
 *
 * @author Stephen Connolly
 */
@Extension(ordinal = -100.0) // run this last as it blocks the channel
public class ThreadDumps extends ObjectComponent<Computer> {

    private static final String DATE_FORMAT = "yyyyMMdd-HHmmss.SSS";

    private static final Logger LOGGER = Logger.getLogger(ThreadDumps.class.getName());

    @DataBoundConstructor
    public ThreadDumps() {}

    /**
     * Collects multiple thread dumps and saves them to the specified file list.
     * @param fileList the file list where the thread dumps will be saved
     * @param timestamp the initial timestamp
     * @param delay the delay between two consecutive thread dumps
     * @param iterations the number of thread dumps to collect
     */
    public static void collectMultiple(FileListCap fileList, long timestamp, long delay, int iterations) {
        collectMultiple(fileList, timestamp, delay, iterations, Level.WARNING);
    }

    /**
     * Collects multiple thread dumps and saves them to the specified file list.
     * @param fileList the file list where the thread dumps will be saved
     * @param timestamp the initial timestamp
     * @param delay the delay between two consecutive thread dumps
     * @param iterations the number of thread dumps to collect
     * @param loggingLevel the logging level to use for logging errors
     */
    public static void collectMultiple(
            FileListCap fileList, long timestamp, long delay, int iterations, Level loggingLevel) {
        if (delay <= 0) {
            throw new IllegalArgumentException("delay must be greater than 0");
        }
        var format = new SimpleDateFormat(DATE_FORMAT);
        File threadDumpFile = fileList.file(format.format(new Date(timestamp)) + ".txt");
        try (FileOutputStream fileOutputStream = new FileOutputStream(threadDumpFile)) {
            threadDump(fileOutputStream);
            fileList.add(threadDumpFile);
        } catch (IOException ioe) {
            LOGGER.log(loggingLevel, "Failed to generate thread dump", ioe);
        } finally {
            if (iterations > 1) {
                Timer.get()
                        .schedule(
                                () -> collectMultiple(fileList, timestamp + delay, delay, iterations - 1, loggingLevel),
                                delay,
                                TimeUnit.MILLISECONDS);
            }
        }
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "Thread dumps";
    }

    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz) || Computer.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean isApplicable(Computer item) {
        return item != Jenkins.get().toComputer();
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new Content("nodes/master/thread-dump.txt") {
            @Override
            public void writeTo(OutputStream os) throws IOException {
                PrintWriter out =
                        new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)));
                try {
                    out.println("Master");
                    out.println("======");
                    out.println();
                } finally {
                    out.flush();
                }
                try {
                    Timer.get()
                            .submit(() -> {
                                /* OK */
                            })
                            .get(10, TimeUnit.SECONDS);
                } catch (ExecutionException | InterruptedException x) {
                    LOGGER.log(Level.WARNING, null, x);
                } catch (TimeoutException x) {
                    out.println("*WARNING*: jenkins.util.Timer is unresponsive");
                }
                try {
                    threadDump(os);
                } finally {
                    os.flush();
                }
            }
        });
        Jenkins.get().getNodes().stream()
                .map(Node::toComputer)
                .filter(Objects::nonNull)
                .forEach(computer -> addContents(result, computer));
    }

    @Override
    public void addContents(@NonNull Container container, @NonNull Computer item) {
        Node node = item.getNode();
        if (node == null) {
            return;
        }
        // let's start collecting thread dumps now... this gives us until the end of the bundle to finish
        final Future<String> threadDump;
        try {
            threadDump = getThreadDump(node);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not record thread dump for " + node.getNodeName(), e);
            final StringWriter sw = new StringWriter();
            PrintWriter out = new PrintWriter(sw);
            Functions.printStackTrace(e, out);
            out.close();
            container.add(new StringContent(
                    "nodes/slave/{0}/thread-dump.txt", new String[] {node.getNodeName()}, sw.toString()));
            return;
        }
        if (threadDump == null) {
            StringBuilder buf = new StringBuilder();
            buf.append(node.getNodeName()).append("\n");
            buf.append("======\n");
            buf.append("\n");
            buf.append("N/A: No connection to node.\n");
            container.add(new StringContent(
                    "nodes/slave/{0}/thread-dump.txt", new String[] {node.getNodeName()}, buf.toString()));
        } else {
            container.add(new Content("nodes/slave/{0}/thread-dump.txt", node.getNodeName()) {
                @Override
                public void writeTo(OutputStream os) throws IOException {
                    PrintWriter out =
                            new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)));
                    try {
                        out.println(node.getNodeName());
                        out.println("======");
                        out.println();
                        String content = null;
                        try {
                            // We want to wait here a bit longer than normal
                            // as we will not fall back to a cache
                            content = threadDump.get(
                                    Math.min(
                                            SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS * 8L,
                                            TimeUnit.SECONDS.toMillis(
                                                    SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC)),
                                    TimeUnit.MILLISECONDS);
                        } catch (InterruptedException | ExecutionException e) {
                            LOGGER.log(Level.WARNING, "Could not record thread dump for " + node.getNodeName(), e);
                            Functions.printStackTrace(e, out);
                        } catch (TimeoutException e) {
                            LOGGER.log(Level.WARNING, "Could not record thread dump for " + node.getNodeName(), e);
                            Functions.printStackTrace(e, out);
                            threadDump.cancel(true);
                        }
                        if (content != null) {
                            out.println(content);
                        }
                    } finally {
                        out.flush();
                    }
                }
            });
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }

    public Future<String> getThreadDump(Node node) throws IOException {
        VirtualChannel channel = node.getChannel();
        if (channel == null) {
            return null;
        }
        return CallAsyncWrapper.callAsync(channel, new GetThreadDump());
    }

    @Deprecated
    public static String getThreadDump(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null) {
            return "N/A: No connection to node.";
        }
        return channel.call(new GetThreadDump());
    }

    private static final class GetThreadDump extends MasterToSlaveCallable<String, RuntimeException> {

        public String call() {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                threadDump(bos);
                return bos.toString(StandardCharsets.UTF_8);
            } catch (UnsupportedEncodingException e) {
                return bos.toString(Charset.defaultCharset());
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Dumps all of the threads' current information to an output stream.
     *
     * @param out an output stream.
     * @throws UnsupportedEncodingException if the utf-8 encoding is not supported.
     */
    public static void threadDump(OutputStream out) throws UnsupportedEncodingException {
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);

        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads;
        try {
            threads = mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(), mbean.isSynchronizerUsageSupported());
        } catch (UnsupportedOperationException x) {
            Functions.printStackTrace(x, writer);
            threads = new ThreadInfo[0];
        }

        Arrays.sort(threads, Comparator.comparing(ThreadInfo::getThreadName));
        for (ThreadInfo t : threads) {
            printThreadInfo(writer, t, mbean);
        }

        // Print any information about deadlocks.
        long[] deadLocks;
        try {
            deadLocks = mbean.findDeadlockedThreads();
        } catch (UnsupportedOperationException x) {
            Functions.printStackTrace(x, writer);
            deadLocks = null;
        }
        if (deadLocks != null && deadLocks.length != 0) {
            writer.println(" Deadlock Found ");
            ThreadInfo[] deadLockThreads = mbean.getThreadInfo(deadLocks);
            for (ThreadInfo threadInfo : deadLockThreads) {
                StackTraceElement[] elements = threadInfo.getStackTrace();
                for (StackTraceElement element : elements) {
                    writer.println(element.toString());
                }
            }
        }

        writer.println();
        writer.flush();
    }

    public static void printThreadInfo(PrintWriter writer, ThreadInfo t, ThreadMXBean mbean) {
        printThreadInfo(writer, t, mbean, ContentFilter.NONE);
    }

    /**
     * Prints the {@link ThreadInfo} (because {@link ThreadInfo#toString()} caps out the stack trace at 8 frames). It
     * filters the content with the filter. It's used by other components, like {@link com.cloudbees.jenkins.support.timer.DeadlockRequestComponent}
     * via {@link com.cloudbees.jenkins.support.timer.DeadlockTrackChecker}
     *
     * @param writer the writer to print to.
     * @param t      the thread to print
     * @param mbean  the {@link ThreadMXBean} to use.
     * @param filter the {@link ContentFilter} to use for filtering the thread name.
     */
    public static void printThreadInfo(
            PrintWriter writer, ThreadInfo t, ThreadMXBean mbean, @NonNull ContentFilter filter) {
        long cpuPercentage;
        try {
            long cpuTime = mbean.getThreadCpuTime(t.getThreadId());
            long threadUserTime = mbean.getThreadUserTime(t.getThreadId());
            cpuPercentage = (cpuTime == 0) ? 0 : 100 * threadUserTime / cpuTime;
        } catch (UnsupportedOperationException x) {
            Functions.printStackTrace(x, writer);
            cpuPercentage = 0;
        }
        writer.printf(
                "\"%s\" id=%d (0x%x) state=%s cpu=%d%%",
                ContentFilter.filter(filter, t.getThreadName()),
                t.getThreadId(),
                t.getThreadId(),
                t.getThreadState(),
                cpuPercentage);
        final LockInfo lock = t.getLockInfo();
        if (lock != null && t.getThreadState() != Thread.State.BLOCKED) {
            writer.printf("%n    - waiting on <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName());
            writer.printf("%n    - locked <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName());
        } else if (lock != null && t.getThreadState() == Thread.State.BLOCKED) {
            writer.printf("%n    - waiting to lock <0x%08x> (a %s)", lock.getIdentityHashCode(), lock.getClassName());
        }

        if (t.isSuspended()) {
            writer.print(" (suspended)");
        }

        if (t.isInNative()) {
            writer.print(" (running in native)");
        }

        writer.println();
        if (t.getLockOwnerName() != null) {
            writer.printf(
                    "      owned by \"%s\" id=%d (0x%x)%n",
                    ContentFilter.filter(filter, t.getLockOwnerName()), t.getLockOwnerId(), t.getLockOwnerId());
        }

        final StackTraceElement[] elements = t.getStackTrace();
        final MonitorInfo[] monitors = t.getLockedMonitors();

        for (int i = 0; i < elements.length; i++) {
            final StackTraceElement element = elements[i];
            writer.printf("    at %s%n", element);
            for (int j = 1; j < monitors.length; j++) {
                final MonitorInfo monitor = monitors[j];
                if (monitor.getLockedStackDepth() == i) {
                    writer.printf("      - locked %s%n", monitor);
                }
            }
        }
        writer.println();

        final LockInfo[] locks = t.getLockedSynchronizers();
        if (locks.length > 0) {
            writer.printf("    Locked synchronizers: count = %d%n", locks.length);
            for (LockInfo l : locks) {
                writer.printf("      - %s%n", l);
            }
            writer.println();
        }
    }

    /** @deprecated use {@link #threadDump} */
    @Deprecated
    public static void threadDumpModern(OutputStream out) throws UnsupportedEncodingException {
        threadDump(out);
    }

    /** @deprecated use {@link #threadDump} */
    @Deprecated
    public static void threadDumpLegacy(OutputStream out) throws UnsupportedEncodingException {
        threadDump(out);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("threadDumpsComponent")
    public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Agent Thread Dumps";
        }
    }
}
