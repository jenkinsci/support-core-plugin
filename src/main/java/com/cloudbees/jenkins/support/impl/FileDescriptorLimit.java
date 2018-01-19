package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.sun.management.UnixOperatingSystemMXBean;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import jenkins.security.MasterToSlaveCallable;

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
        Jenkins j = Jenkins.getInstance();
        addContents(container, j);
        for (Node node : j.getNodes()) {
            addContents(container, node);
        }
    }

    private void addContents(@NonNull Container container, final @NonNull Node node) {
        Computer c = node.toComputer();
        if (c == null) {
            return;
        }
        if (c instanceof SlaveComputer && !Boolean.TRUE.equals(((SlaveComputer) c).isUnix())) {
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
                    new Content("nodes/" + name + "/file-descriptors.txt") {
                        @Override
                        public void writeTo(OutputStream os) throws IOException {
                            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                            out.println(node.getDisplayName());
                            out.println("======");
                            out.println();
                            try {
                                out.println(getUlimit(node));
                            } catch (IOException e) {
                                SupportLogFormatter.printStackTrace(e, out);
                            } finally {
                                out.flush();
                            }
                        }
                    }
            );
    }

    public String getUlimit(Node node) throws IOException {
        return AsyncResultCache.get(node, fileDescriptorCache, new GetUlimit(), "file descriptor info",
                "N/A: Either no connection to node or no cached result");
    }

    @Deprecated
    public static String getUlimit(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null) {
            return "N/A: No connection to node.";
        }
        return channel.call(new GetUlimit());
    }

    /**
     * * For agent machines.
     */
    private static final class GetUlimit extends MasterToSlaveCallable<String, RuntimeException> {
        public String call() {
            StringWriter bos = new StringWriter();
            PrintWriter pw = new PrintWriter(bos);
            try {
                getUlimit(pw);
            } catch (Exception e) {
                SupportLogFormatter.printStackTrace(e, pw);
            }
            try {
                getOpenFileDescriptorCount(pw);
            } catch (Exception e) {
                SupportLogFormatter.printStackTrace(e, pw);
            }
            try {
                listAllOpenFileDescriptors(pw);
            } catch (Exception e) {
                SupportLogFormatter.printStackTrace(e, pw);
            }
            pw.flush();
            return bos.toString();
        }
    }

    /**
     * * Using OperatingSystemMXBean, we can obtain the total number of open file descriptors.
     */
    @IgnoreJRERequirement // UnixOperatingSystemMXBean
    private static void getOpenFileDescriptorCount(PrintWriter writer) throws UnsupportedEncodingException {
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
    private static void listAllOpenFileDescriptors(PrintWriter writer) throws IOException, InterruptedException {
        writer.println();
        writer.println("All open files");
        writer.println("==============");
        File[] files = new File("/proc/self/fd").listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    writer.println(Util.resolveSymlink(file));
                } catch (IOException e) {
                    // If we fail to resolve the symlink, just print the file.
                    writer.println(file.getCanonicalPath());
                }
            }
        }
    }

    /**
     * This method executes the command "bash -c ulimit -a" on the machine.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings({"DM_DEFAULT_ENCODING", "OS_OPEN_STREAM"})
    private static void getUlimit(PrintWriter writer) throws IOException {
        // TODO should first check whether /bin/bash even exists
        InputStream is = new ProcessBuilder("bash", "-c", "ulimit -a").start().getInputStream();
        try {
            // this is reading from the process so platform encoding is correct
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                writer.println(line);
            }
        } finally {
            is.close();
        }
    }
}
