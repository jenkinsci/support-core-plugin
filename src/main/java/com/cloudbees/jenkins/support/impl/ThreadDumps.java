package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
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
            result.add(
                    new Content("nodes/slave/" + node.getDisplayName() + "/thread-dump.txt") {
                        @Override
                        public void writeTo(OutputStream os) throws IOException {
                            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                            out.println(node.getDisplayName());
                            out.println("======");
                            out.println();
                            try {
                                out.println(getThreadDump(node.getChannel()));
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "Could not record thread dump for " + node.getDisplayName(),
                                        e);
                            } catch (InterruptedException e) {
                                logger.log(Level.WARNING, "Could not record thread dump for " + node.getDisplayName(),
                                        e);
                            } finally {
                                out.flush();
                            }
                        }
                    }
            );

        }
    }

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
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        final ThreadInfo[] threads =
                mbean.dumpAllThreads(mbean.isObjectMonitorUsageSupported(), mbean.isSynchronizerUsageSupported());

        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "utf-8"), true);

        for (int ti = threads.length - 1; ti >= 0; ti--) {
            final ThreadInfo t = threads[ti];
            final long cpuPercentage = (mbean.getThreadCpuTime(t.getThreadId()) == 0) ? 0:
                        mbean.getThreadUserTime(t.getThreadId()) / mbean.getThreadCpuTime(t.getThreadId());
            writer.printf("%s id=%d (0x%x) state=%s cpu=%d%%",
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
            writer.printf("%s id=%d (0x%x) state=%s",
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
