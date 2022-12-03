package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.sun.management.UnixOperatingSystemMXBean;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author schristou88
 */
@Extension
public class FileDescriptorLimit extends Component {

    private final WeakHashMap<Node,String> fileDescriptorCache = new WeakHashMap<Node, String>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "File descriptors (Unix only)";
    }

    @Override
    public void addContents(@NonNull Container container) {
        Jenkins j = Jenkins.get();
        addContents(container, j);
        for (Node node : j.getNodes()) {
            addContents(container, node);
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }

    private void addContents(@NonNull Container container, final @NonNull Node node) {
        Computer c = node.toComputer();
        if (c == null) {
            return;
        }
        if (c instanceof SlaveComputer && !Boolean.TRUE.equals(c.isUnix())) {
            return;
        }
        if (!node.createLauncher(TaskListener.NULL).isUnix()) {
            return;
        }
        String name;
        if (node instanceof Jenkins) {
            name = "master";
        } else {
            name = "slave/" + node.getNodeName();
        }
        container.add(
            new PrefilteredPrintedContent("nodes/{0}/file-descriptors.txt", name) {

                @Override
                protected void printTo(PrintWriter out, ContentFilter filter) {
                    out.println(node.getDisplayName());
                    out.println("======");
                    out.println();
                    try {
                        out.println(AsyncResultCache.get(node, fileDescriptorCache, new GetUlimit(filter),
                            "file descriptor info", "N/A: Either no connection to node or no cached result"));
                    } catch (IOException e) {
                        Functions.printStackTrace(e, out);
                    } finally {
                        out.flush();
                    }
                }
            }
        );
    }

    @Deprecated
    public static String getUlimit(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null) {
            return "N/A: No connection to node.";
        }
        return channel.call(new GetUlimit(SupportPlugin.getContentFilter().orElse(null)));
    }

    /**
     * * For agent machines.
     */
    private static final class GetUlimit extends MasterToSlaveCallable<String, RuntimeException> {
        
        private final ContentFilter filter;

        public GetUlimit(ContentFilter filter) {
            this.filter = filter;
        }

        public String call() {
            StringWriter bos = new StringWriter();
            PrintWriter pw = new PrintWriter(bos);
            try {
                getUlimit(pw);
            } catch (Exception e) {
                Functions.printStackTrace(e, pw);
            }
            try {
                getOpenFileDescriptorCount(pw);
            } catch (Exception e) {
                Functions.printStackTrace(e, pw);
            }
            try {
                listAllOpenFileDescriptors(pw, filter);
            } catch (Exception e) {
                Functions.printStackTrace(e, pw);
            }
            pw.flush();
            return bos.toString();
        }
    }

    /**
     * * Using OperatingSystemMXBean, we can obtain the total number of open file descriptors.
     */
    private static void getOpenFileDescriptorCount(PrintWriter writer) {
        try {
            OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
            if (operatingSystemMXBean instanceof UnixOperatingSystemMXBean) {
                UnixOperatingSystemMXBean unixOperatingSystemMXBean = (UnixOperatingSystemMXBean) operatingSystemMXBean;
                writer.println("Open File Descriptor Count: " + unixOperatingSystemMXBean.getOpenFileDescriptorCount());
            } else {
                writer.println("Wrong bean: " + operatingSystemMXBean);
            }
        } catch(LinkageError e) {
            writer.println("Unable to get the total number of open file descriptors using OperatingSystemMXBean");
        }
    }

    /**
     * * List all the open file descriptors. For Unix systems this information can be obtained by
     * * going to /proc/self/fd. This will translate self to the correct PID of the current java
     * * process. Each file in the folder is a symlink to the location of the file descriptor.
     */
    @SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "Intentional")
    private static void listAllOpenFileDescriptors(PrintWriter writer, ContentFilter filter) throws IOException {
        writer.println();
        writer.println("All open files");
        writer.println("==============");
        File[] files = new File("/proc/self/fd").listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    writer.println(ContentFilter.filter(filter, Objects.requireNonNull(Util.resolveSymlink(file))));
                } catch (NullPointerException | IOException e) {
                    // If we fail to resolve the symlink, just print the file.
                    writer.println(ContentFilter.filter(filter, file.getCanonicalPath()));
                }
            }
        }
    }

    /**
     * This method executes the command "bash -c ulimit -a" on the machine.
     */
    @SuppressFBWarnings({"DM_DEFAULT_ENCODING", "OS_OPEN_STREAM"})
    private static void getUlimit(PrintWriter writer) throws IOException {
        // TODO should first check whether /bin/bash even exists
        try (InputStream is = new ProcessBuilder("bash", "-c", "ulimit -a").start().getInputStream()) {
            // this is reading from the process so platform encoding is correct
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                writer.println(line);
            }
        }
    }
}
