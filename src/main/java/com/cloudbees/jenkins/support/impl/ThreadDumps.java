package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.StringContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.*;
import java.lang.management.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread dumps from the nodes.
 *
 * @author Stephen Connolly
 */
@Extension(ordinal = -100.0) // run this last as it blocks the channel
public class ThreadDumps extends Component {

    private final Logger logger = Logger.getLogger(ThreadDumps.class.getName());

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
    public void addContents(@NonNull Container result) {
        result.add(
                new Content("nodes/master/thread-dump.txt") {
                    @Override
                    public void writeTo(OutputStream os) throws IOException {
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                        try {
                            out.println("Master");
                            out.println("======");
                            out.println();
                        } finally {
                            out.flush();
                        }
                        try {
                            threadDump(os);
                        } finally {
                            os.flush();
                        }
                    }
                }
        );
        for (final Node node : Jenkins.getInstance().getNodes()) {
            // let's start collecting thread dumps now... this gives us until the end of the bundle to finish
            final Future<String> threadDump;
            try {
                threadDump = getThreadDump(node);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not record thread dump for " + node.getNodeName(), e);
                final StringWriter sw = new StringWriter();
                PrintWriter out = new PrintWriter(sw);
                e.printStackTrace(out);
                out.close();
                result.add(
                        new StringContent("nodes/slave/" + node.getNodeName() + "/thread-dump.txt", sw.toString()));
                continue;
            }
            if (threadDump == null) {
                StringBuilder buf = new StringBuilder();
                buf.append(node.getNodeName()).append("\n");
                buf.append("======\n");
                buf.append("\n");
                buf.append("N/A: No connection to node.\n");
                result.add(new StringContent("nodes/slave/" + node.getNodeName() + "/thread-dump.txt", buf.toString()));
            } else {
                result.add(
                        new Content("nodes/slave/" + node.getNodeName() + "/thread-dump.txt") {
                            @Override
                            public void writeTo(OutputStream os) throws IOException {
                                PrintWriter out =
                                        new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                                try {
                                    out.println(node.getNodeName());
                                    out.println("======");
                                    out.println();
                                    String content = null;
                                    try {
                                        // We want to wait here a bit longer than normal
                                        // as we will not fall back to a cache
                                        content = threadDump.get(Math.min(
                                                SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS * 8,
                                                TimeUnit.SECONDS
                                                        .toMillis(SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC)
                                        ), TimeUnit.MILLISECONDS);
                                    } catch (InterruptedException e) {
                                        logger.log(Level.WARNING,
                                                "Could not record thread dump for " + node.getNodeName(),
                                                e);
                                        e.printStackTrace(out);
                                    } catch (ExecutionException e) {
                                        logger.log(Level.WARNING,
                                                "Could not record thread dump for " + node.getNodeName(),
                                                e);
                                        e.printStackTrace(out);
                                    } catch (TimeoutException e) {
                                        logger.log(Level.WARNING,
                                                "Could not record thread dump for " + node.getNodeName(),
                                                e);
                                        e.printStackTrace(out);
                                        threadDump.cancel(true);
                                    }
                                    if (content != null) {
                                        out.println(content);
                                    }
                                } finally {
                                    out.flush();
                                }
                            }
                        }
                );
            }

        }
    }

    public Future<String> getThreadDump(Node node) throws IOException {
        VirtualChannel channel = node.getChannel();
        if (channel == null) {
            return null;
        }
        return channel.callAsync(new GetThreadDump());
    }

    @Deprecated
    public static String getThreadDump(VirtualChannel channel)
            throws IOException, InterruptedException {
        if (channel == null) {
            return "N/A: No connection to node.";
        }
        return channel.call(new GetThreadDump());
    }

    private static final class GetThreadDump implements Callable<String, RuntimeException> {
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING"},
                justification = "Best effort"
        )
        public String call() {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                threadDump(bos);
                return bos.toString("utf-8");
            } catch (UnsupportedEncodingException e) {
                return bos.toString();
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Dumps all of the threads' current information to an output stream.
     *
     * @param out an output stream
     */
    public static void threadDump(OutputStream out) throws UnsupportedEncodingException {
        try {
            threadDumpModern(out);
        } catch (LinkageError e) {
            threadDumpLegacy(out);
        }

    }

    /**
     * Dumps all of the threads' current information to an output stream.
     *
     * @param out an output stream
     */
    @IgnoreJRERequirement
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"VA_FORMAT_STRING_USES_NEWLINE"},
            justification = "We don't want platform specific"
    )
    public static void threadDumpModern(OutputStream out) throws UnsupportedEncodingException {
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "utf-8"), true);

        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threads;
        try {
            threads = mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(), mbean.isSynchronizerUsageSupported());
        } catch (UnsupportedOperationException x) {
            x.printStackTrace(writer);
            threads = new ThreadInfo[0];
        }

        for (int ti = threads.length - 1; ti >= 0; ti--) {
            final ThreadInfo t = threads[ti];
            long cpuPercentage;
            try {
                long cpuTime = mbean.getThreadCpuTime(t.getThreadId());
                long threadUserTime = mbean.getThreadUserTime(t.getThreadId());
                cpuPercentage = (cpuTime == 0) ? 0: 100 * threadUserTime / cpuTime;
            } catch (UnsupportedOperationException x) {
                x.printStackTrace(writer);
                cpuPercentage = 0;
            }
            writer.printf("\"%s\" id=%d (0x%x) state=%s cpu=%d%%",
                    t.getThreadName(),
                    t.getThreadId(),
                    t.getThreadId(),
                    t.getThreadState(),
                    cpuPercentage);
            final LockInfo lock = t.getLockInfo();
            if (lock != null && t.getThreadState() != Thread.State.BLOCKED) {
                writer.printf("\n    - waiting on <0x%08x> (a %s)",
                        lock.getIdentityHashCode(),
                        lock.getClassName());
                writer.printf("\n    - locked <0x%08x> (a %s)",
                        lock.getIdentityHashCode(),
                        lock.getClassName());
            } else if (lock != null && t.getThreadState() == Thread.State.BLOCKED) {
                writer.printf("\n    - waiting to lock <0x%08x> (a %s)",
                        lock.getIdentityHashCode(),
                        lock.getClassName());
            }

            if (t.isSuspended()) {
                writer.print(" (suspended)");
            }

            if (t.isInNative()) {
                writer.print(" (running in native)");
            }

            writer.println();
            if (t.getLockOwnerName() != null) {
                writer.printf("     owned by %s id=%d\n", t.getLockOwnerName(), t.getLockOwnerId());
            }

            final StackTraceElement[] elements = t.getStackTrace();
            final MonitorInfo[] monitors = t.getLockedMonitors();

            for (int i = 0; i < elements.length; i++) {
                final StackTraceElement element = elements[i];
                writer.printf("    at %s\n", element);
                for (int j = 1; j < monitors.length; j++) {
                    final MonitorInfo monitor = monitors[j];
                    if (monitor.getLockedStackDepth() == i) {
                        writer.printf("      - locked %s\n", monitor);
                    }
                }
            }
            writer.println();

            final LockInfo[] locks = t.getLockedSynchronizers();
            if (locks.length > 0) {
                writer.printf("    Locked synchronizers: count = %d\n", locks.length);
                for (LockInfo l : locks) {
                    writer.printf("      - %s\n", l);
                }
                writer.println();
            }
        }

        // Print any information about deadlocks.
        long[] deadLocks;
        try {
            deadLocks = mbean.findDeadlockedThreads();
        } catch (UnsupportedOperationException x) {
            x.printStackTrace(writer);
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

    /**
     * Dumps all of the threads' current information to an output stream.
     *
     * @param out an output stream
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"VA_FORMAT_STRING_USES_NEWLINE"},
            justification = "We don't want platform specific"
    )
    public static void threadDumpLegacy(OutputStream out) throws UnsupportedEncodingException {
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "utf-8"), true);
        for (Map.Entry<Thread, StackTraceElement[]> entry : Functions.dumpAllThreads().entrySet()) {
            final Thread t = entry.getKey();
            writer.printf("\"%s\" id=%d (0x%x) state=%s",
                    t.getName(),
                    t.getId(),
                    t.getId(),
                    t.getState());
            writer.println();

            final StackTraceElement[] elements = entry.getValue();

            for (final StackTraceElement element : elements) {
                writer.printf("    at %s\n", element);
            }
            writer.println();
        }

        writer.println();
        writer.flush();
    }

}
