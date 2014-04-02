package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.sun.management.UnixOperatingSystemMXBean;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author schristou88
 */
@Extension
public class FileDescriptorLimit extends Component {
    private final Logger logger = Logger.getLogger(FileDescriptorLimit.class.getCanonicalName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "File Descriptor Properties (Linux only)";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new Content("nodes/master/file-descriptors.txt") {
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
                    getUlimit(out);
                    getOpenFileDescriptorCount(out);
                    listAllOpenFileDescriptors(out);
                } finally {
                    os.flush();
                }
            }
        });

        for (final Node node : Jenkins.getInstance().getNodes()) {
            container.add(
                    new Content("nodes/slave/" + node.getDisplayName() + "/file-descriptors.txt") {
                        @Override
                        public void writeTo(OutputStream os) throws IOException {
                            PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                            out.println(node.getDisplayName());
                            out.println("======");
                            out.println();
                            try {
                                out.println(getUlimit(node.getChannel()));
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "Could not record thread dump for " + node.getDisplayName(), e);
                            } catch (InterruptedException e) {
                                logger.log(Level.WARNING, "Could not record thread dump for " + node.getDisplayName(), e);
                            } finally {
                                out.flush();
                            }
                        }
                    }
            );
        }
    }

    public static String getUlimit(VirtualChannel channel) throws IOException, InterruptedException {
        if (channel == null) {
            return "N/A: No connection to node.";
        }
        return channel.call(new GetUlimit());
    }

    /**
     * * For slave machines.
     */
    private static final class GetUlimit implements Callable<String, RuntimeException> {
        public String call() {
            StringWriter bos = new StringWriter();
            PrintWriter pw = new PrintWriter(bos);
            try {
                getUlimit(pw);
                getOpenFileDescriptorCount(pw);
                listAllOpenFileDescriptors(pw);
                return bos.toString();
            } catch (Exception e) {
                return bos.toString();
            }
        }
    }

    /**
     * * Using OperatingSystemMXBean, we can obtain the total number of open file descriptors.
     */
    private static void getOpenFileDescriptorCount(PrintWriter writer) throws UnsupportedEncodingException {
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystemMXBean instanceof UnixOperatingSystemMXBean) {
            UnixOperatingSystemMXBean unixOperatingSystemMXBean = (UnixOperatingSystemMXBean) operatingSystemMXBean;
            writer.println("Open File Descriptor Count: " + unixOperatingSystemMXBean.getOpenFileDescriptorCount());
        }
        writer.flush();
    }

    /**
     * * List all the open file descriptors. For Unix systems this information can be obtained by
     * * going to /proc/self/fd. This will translate self to the correct PID of the current java
     * * process. Each file in the folder is a symlink to the location of the file descriptor.
     */
    private static void listAllOpenFileDescriptors(PrintWriter writer) throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName != null && !osName.contains("win")) { // If unix
            writer.println();
            writer.println("All open files");
            writer.println("==============");
            File[] files = new File("/proc/self/fd").listFiles();
            for (File file : files) {
                writer.println(file.getCanonicalPath());
            }
        }
    }

    /**
     * * This method executes the command "bash -c ulimit -a" on the machine. If an exception is thrown log it
     * * to file and continue.
     */
    private static void getUlimit(PrintWriter writer) throws UnsupportedEncodingException {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName != null && !osName.contains("win")) {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", "ulimit -a");
            Process process;
            BufferedReader bufferedReader = null;
            try {
                process = builder.start();
                bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = "";
                while ((line = bufferedReader.readLine()) != null) {
                    writer.println(line);
                }
            } catch (Exception e) {
                e.printStackTrace(writer); // Print exception in file.
            } finally {
                IOUtils.closeQuietly(bufferedReader);
            }
        }
        writer.flush();
    }
}
