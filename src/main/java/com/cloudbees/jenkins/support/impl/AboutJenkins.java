/*
 * Copyright © 2013 CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import javax.servlet.ServletContext;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;

/**
 * Contributes basic information about Jenkins.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutJenkins extends Component {

    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    private final WeakHashMap<Node,String> slaveVersionCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> javaInfoCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> slaveDigestCache = new WeakHashMap<Node, String>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        // Was originally READ, but a lot of the details here could be considered sensitive:
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "About Jenkins";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new AboutContent());
        container.add(new ItemsContent());
        container.add(new NodesContent());
        container.add(new PrintedContent("plugins/active.txt") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
                List<PluginWrapper> plugins = pluginManager.getPlugins();
                Collections.sort(plugins);
                for (PluginWrapper w : plugins) {
                    if (w.isActive()) {
                        out.println(w.getShortName() + ":" + w.getVersion());
                    }
                }
            }
        });
        container.add(new PrintedContent("plugins/disabled.txt") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
                List<PluginWrapper> plugins = pluginManager.getPlugins();
                Collections.sort(plugins);
                for (PluginWrapper w : plugins) {
                    if (!w.isActive()) {
                        out.println(w.getShortName() + ":" + w.getVersion());
                    }
                }
            }
        });
        container.add(new PrintedContent("plugins/failed.txt") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
                List<PluginManager.FailedPlugin> plugins = pluginManager.getFailedPlugins();
                // no need to sort
                for (PluginManager.FailedPlugin w : plugins) {
                    out.println(w.name + " -> " + w.cause);
                }
            }
        });

        container.add(new PrintedContent("docker/Dockerfile") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                out.println("FROM jenkins:" + Jenkins.getVersion().toString());
                out.println("RUN mkdir -p /usr/share/jenkins/ref/plugins/");
                PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
                List<PluginWrapper> plugins = pluginManager.getPlugins();
                Collections.sort(plugins);
                for (PluginWrapper w : plugins) {
                    if (w.isActive()) {
                        out.println("RUN curl $JENKINS_UC/plugins/"+w.getShortName()+"/"+w.getVersion()+"/"+w.getShortName()+".hpi"
                                + " -o /usr/share/jenkins/ref/plugins/"+w.getShortName()+".hpi");
                    }
                }
                out.println("");
            }
        });

        container.add(new MasterChecksumsContent());
        for (final Node node : Jenkins.getInstance().getNodes()) {
            container.add(new NodeChecksumsContent(node));
        }
    }

    private static String getDescriptorName(@CheckForNull Describable<?> d) {
        if (d == null) {
            return "(none)";
        }
        return "`" + d.getClass().getName() + "`";
    }

    /**
     * A pre-check to see if a string is a build timestamp formatted date.
     *
     * @param s the string.
     * @return {@code true} if it is likely that the string will parse as a build timestamp formatted date.
     */
    static boolean mayBeDate(String s) {
        if (s == null || s.length() != "yyyy-MM-dd_HH-mm-ss".length()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '-':
                    switch (i) {
                        case 4:
                        case 7:
                        case 13:
                        case 16:
                            break;
                        default:
                            return false;
                    }
                    break;
                case '_':
                    if (i != 10) {
                        return false;
                    }
                    break;
                case '0':
                case '1':
                    switch (i) {
                        case 4: // -
                        case 7: // -
                        case 10: // _
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '2':
                    switch (i) {
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 10: // _
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '3':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '4':
                case '5':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 8: // days 0-3
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '6':
                case '7':
                case '8':
                case '9':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 8: // days 0-3
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 14: // minutes 0-5
                        case 16: // -
                        case 17: // seconds 0-5
                            return false;
                        default:
                            break;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    private void printHistogram(PrintWriter out, Histogram histogram) {
        out.println("      - Sample size:        " + histogram.getCount());
        Snapshot snapshot = histogram.getSnapshot();
        out.println("      - Average (mean):     " + snapshot.getMean());
        out.println("      - Average (median):   " + snapshot.getMedian());
        out.println("      - Standard deviation: " + snapshot.getStdDev());
        out.println("      - Minimum:            " + snapshot.getMin());
        out.println("      - Maximum:            " + snapshot.getMax());
        out.println("      - 95th percentile:    " + snapshot.get95thPercentile());
        out.println("      - 99th percentile:    " + snapshot.get99thPercentile());
    }

    private static final class GetSlaveDigest implements Callable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String rootPathName;

        public GetSlaveDigest(FilePath rootPath) {
            this.rootPathName = rootPath.getRemote();
        }

        public String call() {
            StringBuilder result = new StringBuilder();
            final File rootPath = new File(this.rootPathName);
            for (File file : rootPath.listFiles()) {
                if (file.isFile()) {
                    try {
                        result.append(Util.getDigestOf(new FileInputStream(file)))
                                .append("  ")
                                .append(file.getName()).append('\n');
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            return result.toString();
        }

    }

    private static class GetSlaveVersion implements Callable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;

        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                value = {"NP_LOAD_OF_KNOWN_NULL_VALUE"},
                justification = "Findbugs mis-diagnosing closeQuietly's built-in null check"
        )
        public String call() throws RuntimeException {
            InputStream is = null;
            try {
                is = hudson.remoting.Channel.class.getResourceAsStream("/jenkins/remoting/jenkins-version.properties");
                if (is == null) {
                    return "N/A";
                }
                Properties properties = new Properties();
                try {
                    properties.load(is);
                    return properties.getProperty("version", "N/A");
                } catch (IOException e) {
                    return "N/A";
                }
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    private static class GetJavaInfo implements Callable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String maj;
        private final String min;

        private GetJavaInfo(String majorBullet, String minorBullet) {
            this.maj = majorBullet;
            this.min = minorBullet;
        }

        @SuppressWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
        public String call() throws RuntimeException {
            StringBuilder result = new StringBuilder();
            Runtime runtime = Runtime.getRuntime();
            result.append(maj).append(" Java\n");
            result.append(min).append(" Home:           `").append(System.getProperty("java.home").replaceAll("`",
                    "&#96;")).append("`\n");
            result.append(min).append(" Vendor:           ").append(System.getProperty("java.vendor")).append("\n");
            result.append(min).append(" Version:          ").append(System.getProperty("java.version")).append("\n");
            long maxMem = runtime.maxMemory();
            long allocMem = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            result.append(min).append(" Maximum memory:   ").append(humanReadableSize(maxMem)).append("\n");
            result.append(min).append(" Allocated memory: ").append(humanReadableSize(allocMem))
                    .append("\n");
            result.append(min).append(" Free memory:      ").append(humanReadableSize(freeMem)).append("\n");
            result.append(min).append(" In-use memory:    ").append(humanReadableSize(allocMem - freeMem)).append("\n");
            result.append(maj).append(" Java Runtime Specification\n");
            result.append(min).append(" Name:    ").append(System.getProperty("java.specification.name")).append("\n");
            result.append(min).append(" Vendor:  ").append(System.getProperty("java.specification.vendor"))
                    .append("\n");
            result.append(min).append(" Version: ").append(System.getProperty("java.specification.version"))
                    .append("\n");
            result.append(maj).append(" JVM Specification\n");
            result.append(min).append(" Name:    ").append(System.getProperty("java.vm.specification.name"))
                    .append("\n");
            result.append(min).append(" Vendor:  ").append(System.getProperty("java.vm.specification.vendor"))
                    .append("\n");
            result.append(min).append(" Version: ").append(System.getProperty("java.vm.specification.version"))
                    .append("\n");
            result.append(maj).append(" JVM Implementation\n");
            result.append(min).append(" Name:    ").append(System.getProperty("java.vm.name")).append("\n");
            result.append(min).append(" Vendor:  ").append(System.getProperty("java.vm.vendor")).append("\n");
            result.append(min).append(" Version: ").append(System.getProperty("java.vm.version")).append("\n");
            result.append(maj).append(" Operating system\n");
            result.append(min).append(" Name:         ").append(System.getProperty("os.name")).append("\n");
            result.append(min).append(" Architecture: ").append(System.getProperty("os.arch")).append("\n");
            result.append(min).append(" Version:      ").append(System.getProperty("os.version")).append("\n");
            File lsb_release = new File("/usr/bin/lsb_release");
            if (lsb_release.canExecute()) {
                try {
                    Process proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--description", "--short").start();
                    String distro = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                    if (proc.waitFor() == 0) {
                        result.append(min).append(" Distribution: ").append(distro).append("\n");
                    } else {
                        logger.fine("lsb_release had a nonzero exit status");
                    }
                    proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--version", "--short").start();
                    String modules = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                    if (proc.waitFor() == 0 && modules != null) {
                        result.append(min).append(" LSB Modules:  `").append(modules).append("`\n");
                    } else {
                        logger.fine("lsb_release had a nonzero exit status");
                    }
                } catch (IOException x) {
                    logger.log(Level.WARNING, "lsb_release exists but could not run it", x);
                } catch (InterruptedException x) {
                    logger.log(Level.WARNING, "lsb_release hung", x);
                }
            }
            RuntimeMXBean mBean = ManagementFactory.getRuntimeMXBean();
            String process = mBean.getName();
            Matcher processMatcher = Pattern.compile("^(-?[0-9]+)@.*$").matcher(process);
            if (processMatcher.matches()) {
                int processId = Integer.parseInt(processMatcher.group(1));
                result.append(maj).append(" Process ID: ").append(processId).append(" (0x")
                        .append(Integer.toHexString(processId)).append(")\n");
            }
            result.append(maj).append(" Process started: ")
                    .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ").format(new Date(mBean.getStartTime())))
                    .append('\n');
            result.append(maj).append(" Process uptime: ")
                    .append(Util.getTimeSpanString(mBean.getUptime())).append('\n');
            result.append(maj).append(" JVM startup parameters:\n");
            if (mBean.isBootClassPathSupported()) {
                result.append(min).append(" Boot classpath: `")
                        .append(mBean.getBootClassPath().replaceAll("`", "&#96;")).append("`\n");
            }
            result.append(min).append(" Classpath: `").append(mBean.getClassPath().replaceAll("`", "&#96;"))
                    .append("`\n");
            result.append(min).append(" Library path: `").append(mBean.getLibraryPath().replaceAll("`", "&#96;"))
                    .append("`\n");
            int count = 0;
            for (String arg : mBean.getInputArguments()) {
                result.append(min).append(" arg[").append(count++).append("]: `").append(arg.replaceAll("`", "&#96;"))
                        .append("`\n");
            }
            return result.toString();
        }

    }

    private static String humanReadableSize(long size) {
        String measure = "B";
        if (size < 1024) {
            return size + " " + measure;
        }
        double number = size;
        if (number >= 1024) {
            number = number / 1024;
            measure = "KB";
            if (number >= 1024) {
                number = number / 1024;
                measure = "MB";
                if (number >= 1024) {
                    number = number / 1024;
                    measure = "GB";
                }
            }
        }
        DecimalFormat format = new DecimalFormat("#0.00");
        return format.format(number) + " " + measure + " (" + size + ")";
    }

    private static class Stats {
        private int s0 = 0;
        private long s1 = 0;
        private long s2 = 0;

        public synchronized void add(int x) {
            s0++;
            s1 += x;
            s2 += x * (long) x;
        }

        public synchronized double x() {
            return s1 / (double) s0;
        }

        private static double roundToSigFig(double num, int sigFig) {
            if (num == 0) {
                return 0;
            }
            final double d = Math.ceil(Math.log10(num < 0 ? -num : num));
            final int pow = sigFig - (int) d;
            final double mag = Math.pow(10, pow);
            final long shifted = Math.round(num * mag);
            return shifted / mag;
        }

        public synchronized double s() {
            if (s0 >= 2) {
                double v = Math.sqrt((s0 * (double) s2 - s1 * (double) s1) / s0 / (s0 - 1));
                if (s0 <= 100) {
                    return roundToSigFig(v, 1); // 0.88*SD to 1.16*SD
                }
                if (s0 <= 1000) {
                    return roundToSigFig(v, 2); // 0.96*SD to 1.05*SD
                }
                return v;
            } else {
                return Double.NaN;
            }
        }

        public synchronized String toString() {
            if (s0 == 0) {
                return "N/A";
            }
            if (s0 == 1) {
                return Long.toString(s1) + " [n=" + s0 + "]";
            }
            return Double.toString(x()) + " [n=" + s0 + ", s=" + s() + "]";
        }

        public synchronized int n() {
            return s0;
        }
    }

    private static class AboutContent extends PrintedContent {
        AboutContent() {
            super("about.md");
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            out.println("Jenkins");
            out.println("=======");
            out.println();
            out.println("Version details");
            out.println("---------------");
            out.println();
            out.println("  * Version: `" + Jenkins.getVersion().toString().replaceAll("`", "&#96;") + "`");
            File jenkinsWar = Lifecycle.get().getHudsonWar();
            if (jenkinsWar == null) {
                out.println("  * Mode:    Webapp Directory");
            } else {
                out.println("  * Mode:    WAR");
            }
            try {
                final ServletContext servletContext = Stapler.getCurrent().getServletContext();
                out.println("  * Servlet container");
                out.println("      - Specification: " + servletContext.getMajorVersion() + "." + servletContext
                        .getMinorVersion());
                out.println(
                        "      - Name:          `" + servletContext.getServerInfo().replaceAll("`", "&#96;") + "`");
            } catch (NullPointerException e) {
                // pity Stapler.getCurrent() throws an NPE when outside of a request
            }
            out.print(new GetJavaInfo("  *", "      -").call());
            out.println();
            out.println("Important configuration");
            out.println("---------------");
            out.println();
            out.println("  * Security realm: " + getDescriptorName(Jenkins.getInstance().getSecurityRealm()));
            out.println("  * Authorization strategy: " + getDescriptorName(Jenkins.getInstance().getAuthorizationStrategy()));
            out.println();
            out.println("Active Plugins");
            out.println("--------------");
            out.println();
            PluginManager pluginManager = Jenkins.getInstance().getPluginManager();
            List<PluginWrapper> plugins = new ArrayList<PluginWrapper>(pluginManager.getPlugins());
            Collections.sort(plugins);
            for (PluginWrapper w : plugins) {
                if (w.isActive()) {
                    out.println("  * " + w.getShortName() + ":" + w.getVersion() + (w.hasUpdate()
                            ? " *(update available)*"
                            : "") + " '" + w.getLongName() + "'");
                }
            }
            SupportPlugin supportPlugin = SupportPlugin.getInstance();
            if (supportPlugin != null) {
                SupportProvider supportProvider = supportPlugin.getSupportProvider();
                if (supportProvider != null) {
                    out.println();
                    try {
                        supportProvider.printAboutJenkins(out);
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, null, e);
                    }
                }
            }
        }
    }

    private static class ItemsContent extends PrintedContent {
        ItemsContent() {
            super("items.md");
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            Map<String,Integer> containerCounts = new TreeMap<String,Integer>();
            Map<String,Stats> jobStats = new HashMap<String,Stats>();
            Stats jobTotal = new Stats();
            Map<String,Stats> containerStats = new HashMap<String,Stats>();
            // RunMap.createDirectoryFilter protected, so must do it by hand:
            DateFormat BUILD_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            for (Item i : Jenkins.getInstance().getAllItems()) {
                String key = i.getClass().getName();
                Integer cnt = containerCounts.get(key);
                containerCounts.put(key, cnt == null ? 1 : cnt + 1);
                if (i instanceof Job) {
                    Job<?,?> j = (Job) i;
                    // too expensive: int builds = j.getBuilds().size();
                    int builds = 0;
                    // protected access: File buildDir = j.getBuildDir();
                    File buildDir = Jenkins.getInstance().getBuildDirFor(j);
                    File[] buildDirs = buildDir.listFiles();
                    if (buildDirs != null) {
                        for (File d : buildDirs) {
                            if (mayBeDate(d.getName())) {
                                // check for real
                                try {
                                    BUILD_FORMAT.parse(d.getName());
                                    if (d.isDirectory()) {
                                        builds++;
                                    }
                                } catch (ParseException x) {
                                    // symlink etc., ignore
                                }
                            }
                        }
                    }
                    jobTotal.add(builds);
                    Stats s = jobStats.get(key);
                    if (s == null) {
                        jobStats.put(key, s = new Stats());
                    }
                    s.add(builds);
                }
                if (i instanceof ItemGroup) {
                    Stats s = containerStats.get(key);
                    if (s == null) {
                        containerStats.put(key, s = new Stats());
                    }
                    s.add(((ItemGroup) i).getItems().size());
                }
            }
            out.println("Item statistics");
            out.println("===============");
            out.println();
            for (Map.Entry<String,Integer> entry : containerCounts.entrySet()) {
                String key = entry.getKey();
                out.println("  * `" + key + "`");
                out.println("    - Number of items: " + entry.getValue());
                Stats s = jobStats.get(key);
                if (s != null) {
                    out.println("    - Number of builds per job: " + s);
                }
                s = containerStats.get(key);
                if (s != null) {
                    out.println("    - Number of items per container: " + s);
                }
            }
            out.println();
            out.println("Total job statistics");
            out.println("======================");
            out.println();
            out.println("  * Number of jobs: " + jobTotal.n());
            out.println("  * Number of builds per job: " + jobTotal);
        }
    }

    private class NodesContent extends PrintedContent {
        NodesContent() {
            super("nodes.md");
        }
        private String getLabelString(Node n) {
            String r = n.getLabelString();
            return r.isEmpty() ? "(none)" : r;
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            SupportPlugin supportPlugin = SupportPlugin.getInstance();
            if (supportPlugin != null) {
                out.println("Node statistics");
                out.println("===============");
                out.println();
                out.println("  * Total number of nodes");
                printHistogram(out, supportPlugin.getJenkinsNodeTotalCount());
                out.println("  * Total number of nodes online");
                printHistogram(out, supportPlugin.getJenkinsNodeOnlineCount());
                out.println("  * Total number of executors");
                printHistogram(out, supportPlugin.getJenkinsExecutorTotalCount());
                out.println("  * Total number of executors in use");
                printHistogram(out, supportPlugin.getJenkinsExecutorUsedCount());
                out.println();
            }
            out.println("Build Nodes");
            out.println("===========");
            out.println();
            out.println("  * master (Jenkins)");
            out.println("      - Description:    _" + Util.fixNull(Jenkins.getInstance().getNodeDescription())
                    .replaceAll("_", "&#95;") + "_");
            out.println("      - Executors:      " + Jenkins.getInstance().getNumExecutors());
            out.println("      - FS root:        `" + Jenkins.getInstance().getRootDir().getAbsolutePath()
                    .replaceAll("`", "&#96;") + "`");
            out.println("      - Labels:         " + getLabelString(Jenkins.getInstance()));
            out.println("      - Usage:          `" + Jenkins.getInstance().getMode() + "`");
            out.print(new GetJavaInfo("      -", "          +").call());
            out.println();
            for (Node node : Jenkins.getInstance().getNodes()) {
                out.println("  * " + node.getNodeName() + " (" + getDescriptorName(node) + ")");
                out.println("      - Description:    _" + Util.fixNull(node.getNodeDescription()).replaceAll("_", "&#95;") + "_");
                out.println("      - Executors:      " + node.getNumExecutors());
                FilePath rootPath = node.getRootPath();
                if (rootPath != null) {
                    out.println("      - Remote FS root: `" + rootPath.getRemote().replaceAll("`", "&#96;")
                            + "`");
                } else if (node instanceof Slave) {
                    out.println("      - Remote FS root: `" + Slave.class.cast(node).getRemoteFS()
                            .replaceAll("`", "&#96;") + "`");
                }
                out.println("      - Labels:         " + getLabelString(node));
                out.println("      - Usage:          `" + node.getMode() + "`");
                if (node instanceof Slave) {
                    Slave slave = (Slave) node;
                    out.println("      - Launch method:  " + getDescriptorName(slave.getLauncher()));
                    out.println("      - Availability:   " + getDescriptorName(slave.getRetentionStrategy()));
                }
                VirtualChannel channel = node.getChannel();
                if (channel == null) {
                    out.println("      - Status:         off-line");
                } else {
                    out.println("      - Status:         on-line");
                    try {
                        out.println("      - Version:        " +
                                AsyncResultCache.get(node, slaveVersionCache, new GetSlaveVersion(),
                                        "slave.jar version", "(timeout with no cache available)"));
                    } catch (IOException e) {
                        logger.log(Level.WARNING,
                                "Could not get slave.jar version for " + node.getNodeName(), e);
                    }
                    try {
                        final String javaInfo = AsyncResultCache.get(node, javaInfoCache,
                                new GetJavaInfo("      -", "          +"), "Java info");
                        if (javaInfo == null) {
                            logger.log(Level.FINE,
                                    "Could not get Java info for {0} and no cached value available",
                                    node.getNodeName());
                        } else {
                            out.print(javaInfo);
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not get Java info for " + node.getNodeName(), e);
                    }
                }
                out.println();
            }
        }
    }

    private static class MasterChecksumsContent extends PrintedContent {
        MasterChecksumsContent() {
            super("nodes/master/checksums.md5");
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            File jenkinsWar = Lifecycle.get().getHudsonWar();
            if (jenkinsWar != null) {
                try {
                    out.println(Util.getDigestOf(new FileInputStream(jenkinsWar)) + "  jenkins.war");
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not compute MD5 of jenkins.war", e);
                }
            }
            Stapler stapler = null;
            try {
                stapler = Stapler.getCurrent();
            } catch (NullPointerException e) {
                // the method is not always safe :-(
            }
            if (stapler != null) {
                final ServletContext servletContext = stapler.getServletContext();
                Set<String> resourcePaths = (Set<String>) servletContext.getResourcePaths("/WEB-INF/lib");
                for (String resourcePath : new TreeSet<String>(resourcePaths)) {
                    try {
                        out.println(
                                Util.getDigestOf(servletContext.getResourceAsStream(resourcePath)) + "  war"
                                        + resourcePath);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                    }
                }
                for (String resourcePath : Arrays.asList(
                        "/WEB-INF/slave.jar",
                        "/WEB-INF/remoting.jar",
                        "/WEB-INF/jenkins-cli.jar",
                        "/WEB-INF/web.xml")) {
                    try {
                        InputStream resourceAsStream = servletContext.getResourceAsStream(resourcePath);
                        if (resourceAsStream == null) {
                            continue;
                        }
                        out.println(
                                Util.getDigestOf(resourceAsStream) + "  war"
                                        + resourcePath);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                    }
                }
                resourcePaths = (Set<String>) servletContext.getResourcePaths("/WEB-INF/update-center-rootCAs");
                for (String resourcePath : new TreeSet<String>(resourcePaths)) {
                    try {
                        out.println(
                                Util.getDigestOf(servletContext.getResourceAsStream(resourcePath)) + "  war"
                                        + resourcePath);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                    }
                }
            }
            for (File file : new File(Jenkins.getInstance().getRootDir(), "plugins").listFiles()) {
                if (file.isFile()) {
                    try {
                        out.println(Util.getDigestOf(new FileInputStream(file)) + "  plugins/" + file
                                .getName());
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war/" + file, e);
                    }
                }
            }
        }
    }

    private class NodeChecksumsContent extends PrintedContent {
        private final Node node;
        NodeChecksumsContent(Node node) {
            super("nodes/slave/" + node.getNodeName() + "/checksums.md5");
            this.node = node;
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            try {
                final FilePath rootPath = node.getRootPath();
                String slaveDigest = rootPath == null ? "N/A" :
                        AsyncResultCache.get(node, slaveDigestCache, new GetSlaveDigest(rootPath),
                                "checksums", "N/A");
                out.println(slaveDigest);
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "Could not compute checksums on slave " + node.getNodeName(), e);
            }
        }
    }

}
