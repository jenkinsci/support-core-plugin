/*
 * Copyright © 2013 CloudBees, Inc.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.filter.PasswordRedactor;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.util.Markdown;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Snapshot;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.lifecycle.Lifecycle;
import hudson.model.Describable;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.JNLPLauncher;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.identity.IdentityRootAction;
import jenkins.security.MasterToSlaveCallable;
import jenkins.slaves.RemotingVersionInfo;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.Stapler;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contributes basic information about Jenkins.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutJenkins extends Component {

    private static final Logger logger = Logger.getLogger(AboutJenkins.class.getName());

    private final WeakHashMap<Node,String> agentVersionCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> javaInfoCache = new WeakHashMap<Node, String>();

    private final WeakHashMap<Node,String> agentDigestCache = new WeakHashMap<Node, String>();

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

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    @Override
    public void addContents(@NonNull Container container) {
        List<PluginWrapper> activePlugins = new ArrayList<PluginWrapper>();
        List<PluginWrapper> disabledPlugins = new ArrayList<PluginWrapper>();
        List<PluginWrapper> backupPlugins = new ArrayList<>();

        populatePluginsLists(activePlugins, disabledPlugins, backupPlugins);

        container.add(new AboutContent(activePlugins));
        container.add(new IdentityContent());
        container.add(new NodesContent());
        container.add(new ActivePlugins(activePlugins));
        container.add(new DisabledPlugins(disabledPlugins));
        container.add(new FailedPlugins());
        container.add(new BackupPlugins(backupPlugins));

        container.add(new Dockerfile(activePlugins, disabledPlugins));

        container.add(new ControllerChecksumsContent());
        for (final Node node : Jenkins.get().getNodes()) {
            container.add(new NodeChecksumsContent(node));
        }
    }

    private static String getDescriptorName(@CheckForNull Describable<?> d) {
        if (d == null) {
            return Markdown.NONE_STRING;
        }
        return "`" + Markdown.escapeBacktick(d.getClass().getName()) + "`";
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

    private static final class GetAgentDigest extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String rootPathName;

        public GetAgentDigest(FilePath rootPath) {
            this.rootPathName = rootPath.getRemote();
        }

        public String call() {
            StringBuilder result = new StringBuilder();
            final File rootPath = new File(this.rootPathName);
            for (File file : FileUtils.listFiles(rootPath, null, false)) {
                if (file.isFile()) {
                    try {
                        result.append(Util.getDigestOf(new FileInputStream(file))) //FIPS OK: Not security related.
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

    private static class GetAgentVersion extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;

        @SuppressFBWarnings(
                value = {"NP_LOAD_OF_KNOWN_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE"},
                justification = "{Findbugs mis-diagnosing closeQuietly's built-in null check, https://github.com/spotbugs/spotbugs/issues/756}"
        )
        public String call() throws RuntimeException {
            try (InputStream is = hudson.remoting.Channel.class.getResourceAsStream("/jenkins/remoting/jenkins-version.properties")) {
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
            } catch (IOException e) {
                logger.fine(String.format("Could not find remoting version in agent {}", e.getMessage()));
                return "N/A";
            }
        }
    }

    private static class GetJavaInfo extends MasterToSlaveCallable<String, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private final String maj;
        private final String min;
        private final PasswordRedactor passwordRedactor;

        private GetJavaInfo(String majorBullet, String minorBullet) {
            this.maj = majorBullet;
            this.min = minorBullet;
            this.passwordRedactor = PasswordRedactor.get();
        }

        public String call() throws RuntimeException {
            return getInfo(null);
        }

        /**
         * Method used to retrieve the info filtered if a filter is set. When used in a node, from the {@link #call()}
         * method, the filter is not passed because it's not going to work in a node through remote.
         * because it doesn't work in an agent.
         * @param filter the filter to use.
         * @return the Java information.
         */
        @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
        private String getInfo(ContentFilter filter) {
            StringBuilder result = new StringBuilder();
            Runtime runtime = Runtime.getRuntime();
            result.append(maj).append(" Java\n");
            result.append(min).append(" Home:           `").append(
                    Markdown.escapeBacktick(System.getProperty("java.home"))).append("`\n");
            result.append(min).append(" Vendor:           ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vendor"))).append("\n");
            result.append(min).append(" Version:          ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.version"))).append("\n");
            long maxMem = runtime.maxMemory();
            long allocMem = runtime.totalMemory();
            long freeMem = runtime.freeMemory();
            result.append(min).append(" Maximum memory:   ").append(humanReadableSize(maxMem)).append("\n");
            result.append(min).append(" Allocated memory: ").append(humanReadableSize(allocMem))
                    .append("\n");
            result.append(min).append(" Free memory:      ").append(humanReadableSize(freeMem)).append("\n");
            result.append(min).append(" In-use memory:    ").append(humanReadableSize(allocMem - freeMem)).append("\n");

            for(MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
                if (bean.getName().toLowerCase().contains("perm gen")) {
                    MemoryUsage currentUsage = bean.getUsage();
                    result.append(min).append(" PermGen used:     ").append(humanReadableSize(currentUsage.getUsed())).append("\n");
                    result.append(min).append(" PermGen max:      ").append(humanReadableSize(currentUsage.getMax())).append("\n");
                    break;
                }
            }

            for(MemoryManagerMXBean bean : ManagementFactory.getMemoryManagerMXBeans()) {
                if (bean.getName().contains("MarkSweepCompact")) {
                    result.append(min).append(" GC strategy:      SerialGC\n");
                    break;
                }
                if (bean.getName().contains("ConcurrentMarkSweep")) {
                    result.append(min).append(" GC strategy:      ConcMarkSweepGC\n");
                    break;
                }
                if (bean.getName().contains("PS")) {
                    result.append(min).append(" GC strategy:      ParallelGC\n");
                    break;
                }
                if (bean.getName().contains("G1")) {
                    result.append(min).append(" GC strategy:      G1\n");
                    break;
                }
            }
            result.append(min).append(" Available CPUs:   ").append(runtime.availableProcessors()).append("\n");

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
            result.append(min).append(" Name:    ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vm.name"))).append("\n");
            result.append(min).append(" Vendor:  ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vm.vendor"))).append("\n");
            result.append(min).append(" Version: ").append(
                    Markdown.escapeUnderscore(System.getProperty("java.vm.version"))).append("\n");
            result.append(maj).append(" Operating system\n");
            result.append(min).append(" Name:         ").append(
                    Markdown.escapeUnderscore(System.getProperty("os.name"))).append("\n");
            result.append(min).append(" Architecture: ").append(
                    Markdown.escapeUnderscore(System.getProperty("os.arch"))).append("\n");
            result.append(min).append(" Version:      ").append(
                    Markdown.escapeUnderscore(System.getProperty("os.version"))).append("\n");
            File lsb_release = new File("/usr/bin/lsb_release");
            if (lsb_release.canExecute()) {
                try {
                    Process proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--description", "--short").start();
                    String distro = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                    if (proc.waitFor() == 0) {
                        result.append(min).append(" Distribution: ").append(
                                Markdown.escapeUnderscore(distro)).append("\n");
                    } else {
                        logger.fine("lsb_release had a nonzero exit status");
                    }
                    proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--version", "--short").start();
                    String modules = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                    if (proc.waitFor() == 0 && modules != null) {
                        result.append(min).append(" LSB Modules:  `").append(
                                Markdown.escapeUnderscore(modules)).append("`\n");
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
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            result.append(maj).append(" Process started: ")
                    .append(f.format(new Date(mBean.getStartTime())))
                    .append('\n');
            result.append(maj).append(" Process uptime: ")
                    .append(Util.getTimeSpanString(mBean.getUptime())).append('\n');
            result.append(maj).append(" JVM startup parameters:\n");
            if (mBean.isBootClassPathSupported()) {
                result.append(min).append(" Boot classpath: `")
                        .append(Markdown.escapeBacktick(mBean.getBootClassPath())).append("`\n");
            }
            result.append(min).append(" Classpath: `").append(Markdown.escapeBacktick(mBean.getClassPath()))
                    .append("`\n");
            result.append(min).append(" Library path: `").append(Markdown.escapeBacktick(mBean.getLibraryPath()))
                    .append("`\n");
            int count = 0;
            for (String arg : mBean.getInputArguments()) {
                // The controller endpoint may be in the args
                arg = passwordRedactor.redact(arg);
                result.append(min).append(" arg[").append(count++).append("]: `").append(Markdown.escapeBacktick(ContentFilter.filter(filter, arg)))
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

    private static class AboutContent extends PrefilteredPrintedContent {
        private final Iterable<PluginWrapper> plugins;

        AboutContent(Iterable<PluginWrapper> plugins) {
            super("about.md");
            this.plugins = plugins;
        }
        @Override
        @SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "pity Stapler.getCurrent() throws an NPE when outside of a request")
        protected void printTo(PrintWriter out, ContentFilter filter) throws IOException {
            final Jenkins jenkins = Jenkins.get();
            out.println("Jenkins");
            out.println("=======");
            out.println();
            out.println("Version details");
            out.println("---------------");
            out.println();
            out.println("  * Version: `" + Markdown.escapeBacktick(Jenkins.VERSION) + "`");
            out.println("  * Instance ID: `" + Markdown.escapeBacktick(Jenkins.get().getLegacyInstanceId()) + "`"); //FIPS OK: Not security related.
            File jenkinsWar = Lifecycle.get().getHudsonWar();
            if (jenkinsWar == null) {
                out.println("  * Mode:    Webapp Directory");
            } else {
                out.println("  * Mode:    WAR");
            }
            final JenkinsLocationConfiguration jlc = JenkinsLocationConfiguration.get();

            out.println("  * Url:     " + (ContentFilter.filter(filter, jlc.getUrl())));
            try {
                final ServletContext servletContext = Stapler.getCurrent().getServletContext();
                out.println("  * Servlet container");
                out.println("      - Specification: " + servletContext.getMajorVersion() + "." + servletContext
                        .getMinorVersion());
                out.println(
                        "      - Name:          `" + Markdown.escapeBacktick(servletContext.getServerInfo()) + "`");
            } catch (NullPointerException e) {
                // pity Stapler.getCurrent() throws an NPE when outside of a request
            }
            out.print(new GetJavaInfo("  *", "      -").getInfo(filter));
            out.println();
            out.println("Remoting details");
            out.println("---------------");
            out.println();
            out.println("  * Embedded Version: `" + Markdown.escapeBacktick(RemotingVersionInfo.getEmbeddedVersion().toString()) + "`");
            out.println("  * Minimum Supported Version: `" + Markdown.escapeBacktick(RemotingVersionInfo.getMinimumSupportedVersion().toString()) + "`");
            out.println();
            out.println("Important configuration");
            out.println("---------------");
            out.println();
            out.println("  * Security realm: " + getDescriptorName(jenkins.getSecurityRealm()));
            out.println("  * Authorization strategy: " + getDescriptorName(jenkins.getAuthorizationStrategy()));
            out.println("  * CSRF Protection: "  + jenkins.isUseCrumbs());
            out.println("  * Initialization Milestone: " + jenkins.getInitLevel());
            out.println("  * Support bundle anonymization: " + ContentFilters.get().isEnabled());
            out.println();
            out.println("Active Plugins");
            out.println("--------------");
            out.println();

            for (PluginWrapper w : plugins) {
                if (w.isActive()) {
                    out.println("  * " + w.getShortName() + ":" + w.getVersion() + (w.hasUpdate()
                            ? " *(update available)*"
                            : "") + " '" + w.getDisplayName() + "'");
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
    
    private static class IdentityContent extends PrintedContent {

        public IdentityContent() {
            super("identity.md");
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            IdentityRootAction idRootAction = ExtensionList.lookupSingleton(IdentityRootAction.class);
            out.println("Public Key");
            out.println("---------------");
            out.println(idRootAction.getPublicKey());
            out.println();
            out.println("Fingerprint");
            out.println("---------------");
            out.println(idRootAction.getFingerprint()); //FIPS OK: Reading ID.
        }
    }

    private static class Plugins extends PrintedContent {
        private final Iterable<PluginWrapper> plugins;
        private final Function<? super PluginWrapper, String> stringify;

        public Plugins(Iterable<PluginWrapper> plugins, String name, Function<? super PluginWrapper, String> stringify) {
            super(name);
            this.plugins = plugins;
            this.stringify = stringify;
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            plugins.forEach(w -> out.println(stringify.apply(w)));
        }

        @Override
        public boolean shouldBeFiltered() {
            return false;
        }
    }

    private static class ActivePlugins extends Plugins {
        public ActivePlugins(Iterable<PluginWrapper> plugins) {
            super(plugins, "plugins/active.txt", w -> w.getShortName() + ":" + w.getVersion());
        }
    }

    private static class DisabledPlugins extends Plugins {
        public DisabledPlugins(Iterable<PluginWrapper> plugins) {
            super(plugins, "plugins/disabled.txt", w -> w.getShortName() + ":" + w.getVersion());
        }
    }

    private static class FailedPlugins extends PrintedContent {
        public FailedPlugins() {
            super("plugins/failed.txt");
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            PluginManager pluginManager = Jenkins.get().getPluginManager();
            List<PluginManager.FailedPlugin> plugins = pluginManager.getFailedPlugins();
            // no need to sort
            for (PluginManager.FailedPlugin w : plugins) {
                out.println(w.name + " -> " + w.cause);
            }
        }

        @Override
        public boolean shouldBeFiltered() {
            return false;
        }
    }

    private static class BackupPlugins extends Plugins {

        public BackupPlugins(Iterable<PluginWrapper> plugins) {
            super(plugins, "plugins/backup.txt", w -> w.getShortName() + ":" + w.getBackupVersion());
        }
    }

    private static class Dockerfile extends PrintedContent {
        private final List<PluginWrapper> activated;
        private final List<PluginWrapper> disabled;

        public Dockerfile(List<PluginWrapper> activated, List<PluginWrapper> disabled) {
            super("docker/Dockerfile");
            this.activated = activated;
            this.disabled = disabled;
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {

            PluginManager pluginManager = Jenkins.get().getPluginManager();
            String fullVersion = Jenkins.VERSION;
            int s = fullVersion.indexOf(' ');
            if (s > 0 && fullVersion.contains("CloudBees")) {
                out.println("FROM cloudbees/jenkins:" + fullVersion.substring(0, s));
            } else {
                out.println("FROM jenkins:" + fullVersion);
            }
            if (pluginManager.getPlugin("nectar-license") != null) { // even if atop an OSS WAR
                out.println("ENV JENKINS_UC http://jenkins-updates.cloudbees.com");
            }

            out.println("RUN mkdir -p /usr/share/jenkins/ref/plugins/");

            out.println("RUN curl \\");
            Iterator<PluginWrapper> activatedIT = activated.iterator();
            while (activatedIT.hasNext()) {
                PluginWrapper w = activatedIT.next();
                out.print("\t-L $JENKINS_UC/download/plugins/" + w.getShortName() + "/" + w.getVersion() + "/" + w.getShortName() + ".hpi"
                        + " -o /usr/share/jenkins/ref/plugins/" + w.getShortName() + ".jpi");
                if (activatedIT.hasNext()) {
                    out.println(" \\");
                }
            }
            out.println();

            /* waiting for official docker image update
            out.println("COPY plugins.txt /plugins.txt");
            out.println("RUN /usr/local/bin/plugins.sh < plugins.txt");
            */

            if (!disabled.isEmpty()) {
                out.println("RUN touch \\");
                Iterator<PluginWrapper> disabledIT = disabled.iterator();
                while (disabledIT.hasNext()) {
                    PluginWrapper w = disabledIT.next();
                    out.print("\n\t/usr/share/jenkins/ref/plugins/" + w.getShortName() + ".jpi.disabled");
                    if (disabledIT.hasNext()) {
                        out.println(" \\");
                    }
                }
                out.println();
            }

            out.println();

        }

        @Override
        public boolean shouldBeFiltered() {
            return false;
        }
    }

    private class NodesContent extends PrefilteredPrintedContent {
        NodesContent() {
            super("nodes.md");
        }
        private String getLabelString(Node n) {
            return Markdown.prettyNone(n.getLabelString());
        }
        @Override protected void printTo(PrintWriter out,  ContentFilter filter) throws IOException {
            final Jenkins jenkins = Jenkins.get();
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
            out.println("      - Description:    _" +
                    Markdown.escapeUnderscore(Util.fixNull(ContentFilter.filter(filter, jenkins.getNodeDescription()))) + "_");
            out.println("      - Executors:      " + jenkins.getNumExecutors());
            out.println("      - FS root:        `" +
                    Markdown.escapeBacktick(ContentFilter.filter(filter, jenkins.getRootDir().getAbsolutePath())) + "`");
            out.println("      - Labels:         " + ContentFilter.filter(filter, getLabelString(jenkins)));
            out.println("      - Usage:          `" + jenkins.getMode() + "`");
            Optional.ofNullable(jenkins.toComputer()).ifPresent(computer ->
                out.println("      - Marked Offline: " + computer.isTemporarilyOffline()));
            if(jenkins.getChannel() == null) {
                out.println("      - Status:         offline");
            } else {
                out.println("      - Status:         on-line");
            }
            out.println("      - Slave Version:  " + Launcher.VERSION);
            out.print(new GetJavaInfo("      -", "          +").getInfo(filter));
            out.println();
            for (Node node : jenkins.getNodes()) {
                out.println("  * `" + Markdown.escapeBacktick(ContentFilter.filter(filter, node.getNodeName())) + "` (" +getDescriptorName(node) +
                        ")");
                out.println("      - Description:    _" +
                        Markdown.escapeUnderscore(Util.fixNull(ContentFilter.filter(filter, node.getNodeDescription()))) + "_");
                out.println("      - Executors:      " + node.getNumExecutors());
                FilePath rootPath = node.getRootPath();
                if (rootPath != null) {
                    out.println("      - Remote FS root: `" + Markdown.escapeBacktick(ContentFilter.filter(filter, rootPath.getRemote())) + "`");
                } else if (node instanceof Slave) {
                    out.println("      - Remote FS root: `" +
                            Markdown.escapeBacktick(ContentFilter.filter(filter, Slave.class.cast(node).getRemoteFS())) + "`");
                }
                out.println("      - Labels:         " + Markdown.escapeUnderscore(ContentFilter.filter(filter, getLabelString(node))));
                out.println("      - Usage:          `" + node.getMode() + "`");
                if (node instanceof Slave) {
                    Slave agent = (Slave) node;
                    out.println("      - Launch method:  " + getDescriptorName(agent.getLauncher()));
                    if (agent.getLauncher() instanceof JNLPLauncher) {
                        out.println("      - WebSocket:      " + ((JNLPLauncher) agent.getLauncher()).isWebSocket());
                    }
                    out.println("      - Availability:   " + getDescriptorName(agent.getRetentionStrategy()));
                }
                Optional.ofNullable(node.toComputer()).ifPresent(computer ->
                    out.println("      - Marked Offline: " + computer.isTemporarilyOffline()));
                VirtualChannel channel = node.getChannel();
                if (channel == null) {
                    out.println("      - Status:         off-line");
                } else {
                    out.println("      - Status:         on-line");
                    try {
                        out.println("      - Version:        " +
                                AsyncResultCache.get(node, agentVersionCache, new GetAgentVersion(),
                                        "agent.jar version", "(timeout with no cache available)"));
                    } catch (IOException e) {
                        logger.log(Level.WARNING,
                                "Could not get agent.jar version for " + node.getNodeName(), e);
                    }
                    try {
                        final String javaInfo = AsyncResultCache.get(node, javaInfoCache,
                                new GetJavaInfo("      -", "          +"), "Java info");
                        if (javaInfo == null) {
                            logger.log(Level.FINE,
                                    "Could not get Java info for {0} and no cached value available",
                                    node.getNodeName());
                        } else {
                            // We make sure the output is filtered, maybe some labels are going to be filtered, but
                            // to avoid that:
                            // TODO: we have to change the MasterToSlaveCallable (GetJavaInfo) call to return a Map of
                            //  values (key: value) and filter all the values here.
                            out.print(ContentFilter.filter(filter, javaInfo));
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not get Java info for " + node.getNodeName(), e);
                    }
                }
                out.println();
            }
        }
    }

    private static class ControllerChecksumsContent extends PrintedContent {
        ControllerChecksumsContent() {
            super("nodes/master/checksums.md5");
        }
        @Override
        @SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "pity Stapler.getCurrent() throws an NPE when outside of a request")
        protected void printTo(PrintWriter out) throws IOException {
            final Jenkins jenkins = Jenkins.get();
            if (jenkins == null) {
                // Lifecycle.get() depends on Jenkins instance, hence this method won't work in any case
                throw new IOException("Jenkins has not been started, or was already shut down");
            }

            File jenkinsWar = Lifecycle.get().getHudsonWar();
            if (jenkinsWar != null) {
                try {
                    out.println(Util.getDigestOf(new FileInputStream(jenkinsWar)) + "  jenkins.war"); //FIPS OK: Not security related.
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
                                Util.getDigestOf(servletContext.getResourceAsStream(resourcePath)) + "  war" //FIPS OK: Not security related.
                                        + resourcePath);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                    }
                }
                for (String resourcePath : Arrays.asList(
                        "/WEB-INF/jenkins-cli.jar",
                        "/WEB-INF/web.xml")) {
                    try {
                        InputStream resourceAsStream = servletContext.getResourceAsStream(resourcePath);
                        if (resourceAsStream == null) {
                            continue;
                        }
                        out.println(
                                Util.getDigestOf(resourceAsStream) + "  war" //FIPS OK: Not security related.
                                        + resourcePath);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                    }
                }
                resourcePaths = (Set<String>) servletContext.getResourcePaths("/WEB-INF/update-center-rootCAs");
                for (String resourcePath : new TreeSet<String>(resourcePaths)) {
                    try {
                        out.println(
                                Util.getDigestOf(servletContext.getResourceAsStream(resourcePath)) + "  war" //FIPS OK: Not security related.
                                        + resourcePath);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war" + resourcePath, e);
                    }
                }
            }

            final Collection<File> pluginFiles = FileUtils.listFiles(new File(jenkins.getRootDir(), "plugins"), null, false);
            for (File file : pluginFiles) {
                if (file.isFile()) {
                    try {
                        out.println(Util.getDigestOf(new FileInputStream(file)) + "  plugins/" + file //FIPS OK: Not security related.
                                .getName());
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not compute MD5 of war/" + file, e);
                    }
                }
            }
        }

        @Override
        public boolean shouldBeFiltered() {
            // The information of this content is not sensible, so it doesn't need to be filtered.
            return false;
        }
    }

    private class NodeChecksumsContent extends PrintedContent {
        private final Node node;
        NodeChecksumsContent(Node node) {
            super("nodes/slave/{0}/checksums.md5", node.getNodeName());
            this.node = node;
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            try {
                final FilePath rootPath = node.getRootPath();
                String agentDigest = rootPath == null ? "N/A" :
                        AsyncResultCache.get(node, agentDigestCache, new GetAgentDigest(rootPath),
                                "checksums", "N/A");
                out.println(agentDigest);
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "Could not compute checksums on agent " + node.getNodeName(), e);
            }
        }

        @Override
        public boolean shouldBeFiltered() {
            // The information of this content is not sensible, so it doesn't need to be filtered.
            return false;
        }
    }

    /**
     * Fixes JENKINS-47779 caused by JENKINS-47713
     * Not using SortedSet because of PluginWrapper doesn't implements equals and hashCode.
     * @return new copy of the PluginManager.getPlugins sorted
     */
    private static Iterable<PluginWrapper> getPluginsSorted() {
        PluginManager pluginManager = Jenkins.get().getPluginManager();
        return getPluginsSorted(pluginManager);
    }

    private static Iterable<PluginWrapper> getPluginsSorted(PluginManager pluginManager) {
        return listToSortedIterable(pluginManager.getPlugins());
    }

    private static <T extends Comparable<T>> Iterable<T> listToSortedIterable(List<T> list) {
        final List<T> sorted = new LinkedList<T>(list);
        Collections.sort(sorted);
        return sorted;
    }

    private static void populatePluginsLists(List<PluginWrapper> activePlugins, List<PluginWrapper> disabledPlugins, List<PluginWrapper> backupPlugins) {
        for(PluginWrapper plugin : getPluginsSorted()){
            if(plugin.isActive()) {
                activePlugins.add(plugin);
            } else {
                disabledPlugins.add(plugin);
            }
            if (plugin.getBackupVersion() != null) {
                backupPlugins.add(plugin);
            }
        }
    }
}
