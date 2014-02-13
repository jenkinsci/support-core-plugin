/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.StringContent;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.api.SupportProviderDescriptor;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Plugin;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.slaves.ComputerListener;
import hudson.util.IOUtils;
import hudson.util.PluginServletFilter;
import hudson.util.TimeUnit2;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.kohsuke.stapler.StaplerRequest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Main entry point for the support plugin.
 *
 * @author Stephen Connolly
 */
public class SupportPlugin extends Plugin {

    public static final PermissionGroup SUPPORT_PERMISSIONS =
            new PermissionGroup(SupportPlugin.class, Messages._SupportPlugin_PermissionGroup());
    public static final Permission CREATE_BUNDLE =
            new Permission(SUPPORT_PERMISSIONS, "DownloadBundle", Messages._SupportPlugin_CreateBundle(),
                    Jenkins.ADMINISTER, PermissionScope.JENKINS);

    private static final ThreadLocal<Authentication> requesterAuthentication = new InheritableThreadLocal
            <Authentication>();
    private static final AtomicLong nextBundleWrite = new AtomicLong(Long.MIN_VALUE);
    private static final Logger logger = Logger.getLogger(SupportPlugin.class.getName());
    private transient final SupportLogHandler handler = new SupportLogHandler(256, 2048, 8);

    private transient SupportContextImpl context = null;
    private transient DefaultSupportMetricsFilter filter;
    private transient Histogram jenkinsQueueLength;
    private transient Histogram jenkinsNodeTotalCount;
    private transient Histogram jenkinsNodeOnlineCount;
    private transient Histogram jenkinsExecutorTotalCount;
    private transient Histogram jenkinsExecutorUsedCount;
    private transient Timer jenkinsBuildDuration;
    private transient Map<Computer, Timer> computerBuildDurations = new HashMap<Computer, Timer>();
    private transient Logger rootLogger;

    private SupportProvider supportProvider;
    
    /** class names of {@link Component} */
    private Set<String> excludedComponents;

    public SupportPlugin() {
        super();
        handler.setLevel(getLogLevel());
        handler.setDirectory(new File(Jenkins.getInstance().getRootDir(), "support"), "all");
    }

    public SupportProvider getSupportProvider() {
        if (supportProvider == null) {
            // if this is not set, pick the first one that we can get our hands on
            for (Descriptor<SupportProvider> d : Jenkins.getInstance().getDescriptorList(SupportProvider.class)) {
                if (d instanceof SupportProviderDescriptor) {
                    try {
                        supportProvider = ((SupportProviderDescriptor) (d)).newDefaultInstance();
                    } catch (Throwable t) {
                        // ignore, we'll try somebody else
                    }
                }
            }
        }
        return supportProvider;
    }

    public static Authentication getRequesterAuthentication() {
        return requesterAuthentication.get();
    }

    public static void setRequesterAuthentication(Authentication authentication) {
        requesterAuthentication.set(authentication);
    }

    public static void clearRequesterAuthentication() {
        requesterAuthentication.remove();
    }

    public void setSupportProvider(SupportProvider supportProvider) throws IOException {
        if (supportProvider != this.supportProvider) {
            this.supportProvider = supportProvider;
            save();
        }
    }

    public Set<String> getExcludedComponents() {
        return excludedComponents != null ? excludedComponents : Collections.<String>emptySet();
    }

    /** @see Component#getId */
    public void setExcludedComponents(Set<String> excludedComponents) throws IOException {
        this.excludedComponents = excludedComponents;
        save();
    }

    public Histogram getJenkinsExecutorTotalCount() {
        return jenkinsExecutorTotalCount;
    }

    public Histogram getJenkinsExecutorUsedCount() {
        return jenkinsExecutorUsedCount;
    }

    public Histogram getJenkinsNodeOnlineCount() {
        return jenkinsNodeOnlineCount;
    }

    public Histogram getJenkinsNodeTotalCount() {
        return jenkinsNodeTotalCount;
    }

    private static Level getLogLevel() {
        return Level.parse(System.getProperty(SupportPlugin.class.getName() + ".LogLevel", "INFO"));
    }

    public static void setLogLevel(String level) {
        setLogLevel(Level.parse(StringUtils.defaultIfEmpty(level, "INFO")));
    }

    public static void setLogLevel(Level level) {
        SupportPlugin instance = getInstance();
        instance.handler.setLevel(level);
        for (Node n : Jenkins.getInstance().getNodes()) {
            Computer c = n.toComputer();
            if (c == null) {
                continue;
            }
            VirtualChannel channel = c.getChannel();
            if (channel != null) {
                try {
                    channel.call(new LogUpdater(level));
                } catch (IOException e) {
                    // ignore
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    public static SupportPlugin getInstance() {
        return Jenkins.getInstance().getPlugin(SupportPlugin.class);
    }

    public static ExtensionList<Component> getComponents() {
        return Jenkins.getInstance().getExtensionList(Component.class);
    }

    public static void writeBundle(OutputStream outputStream) throws IOException {
        writeBundle(outputStream, getComponents());
    }

    public static void writeBundle(OutputStream outputStream, final List<Component> components) throws IOException {
        Logger logger = Logger.getLogger(SupportPlugin.class.getName()); // TODO why is this not SupportPlugin.logger?
        final java.util.Queue<Content> toProcess = new ConcurrentLinkedQueue<Content>();
        final Set<String> names = new TreeSet<String>();
        Container container = new Container() {
            @Override
            public void add(@CheckForNull Content content) {
                if (content != null) {
                    names.add(content.getName());
                    toProcess.add(content);
                }
            }
        };

        StringBuilder manifest = new StringBuilder();
        SupportPlugin plugin = SupportPlugin.getInstance();
        SupportProvider supportProvider = plugin == null ? null : plugin.getSupportProvider();
        String bundleName =
                (supportProvider == null ? "Support" : supportProvider.getDisplayName()) + " Bundle Manifest";
        manifest.append(bundleName).append('\n');
        manifest.append(StringUtils.repeat("=", bundleName.length())).append('\n');
        manifest.append("\n");
        manifest.append("Generated on ")
                .append(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss Z").format(new Date()))
                .append("\n");
        manifest.append("\n");
        manifest.append("Requested components:\n\n");

        StringWriter errors = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(errors);
        for (Component c : components) {
            try {
                manifest.append("  * ").append(c.getDisplayName()).append("\n\n");
                names.clear();
                c.addContents(container);
                for (String name : names) {
                    manifest.append("      - `").append(name).append("`\n\n");
                }
            } catch (Throwable e) {
                String cDisplayName = null;
                try {
                    cDisplayName = c.getDisplayName();
                } catch (Throwable e1) {
                    // be very defensive
                    cDisplayName = c.getClass().getName();
                }
                LogRecord logRecord =
                        new LogRecord(Level.WARNING, "Could not get content from ''{0}'' for support bundle");
                logRecord.setThrown(e);
                logRecord.setParameters(new Object[]{cDisplayName});
                logger.log(logRecord);
                errorWriter.println(
                        MessageFormat.format("Could not get content from ''{0}'' for support bundle", cDisplayName));
                errorWriter.println("-----------------------------------------------------------------------");
                errorWriter.println();
                e.printStackTrace(errorWriter);
                errorWriter.println();

            }
        }
        toProcess.add(new StringContent("manifest.md", manifest.toString()));
        try {
            ZipOutputStream zip = new ZipOutputStream(outputStream);
            try {
                BufferedOutputStream bos = new BufferedOutputStream(zip, 16384) {
                    @Override
                    public void close() throws IOException {
                        // don't let any of the contents accidentally close the zip stream
                        super.flush();
                    }
                };
                while (!toProcess.isEmpty()) {
                    Content c = toProcess.poll();
                    if (c == null) {
                        continue;
                    }
                    final String name = c.getName();
                    try {
                        zip.putNextEntry(new ZipEntry(name));
                        c.writeTo(bos);
                    } catch (Throwable e) {
                        LogRecord logRecord =
                                new LogRecord(Level.WARNING, "Could not attach ''{0}'' to support bundle");
                        logRecord.setThrown(e);
                        logRecord.setParameters(new Object[]{name});
                        logger.log(logRecord);
                        errorWriter.println(MessageFormat.format("Could not attach ''{0}'' to support bundle", name));
                        errorWriter.println("-----------------------------------------------------------------------");
                        errorWriter.println();
                        e.printStackTrace(errorWriter);
                        errorWriter.println();
                    } finally {
                        bos.flush();
                    }
                    zip.flush();
                }
                errorWriter.close();
                String errorContent = errors.toString();
                if (!StringUtils.isBlank(errorContent)) {
                    try {
                        zip.putNextEntry(new ZipEntry("manifest/errors.txt"));
                        zip.write(errorContent.getBytes("utf-8"));
                    } catch (IOException e) {
                        // ignore
                    }
                    zip.flush();
                }
            } finally {
                zip.close();
            }
        } finally {
            outputStream.flush();
        }
    }

    public List<LogRecord> getAllLogRecords() {
        return handler.getRecent();
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void loadConfig() throws IOException {
        getInstance().load();
    }

    @Override
    public synchronized void start() throws Exception {
        super.start();
        rootLogger = Logger.getLogger("");
        rootLogger.addHandler(handler);
        context = new SupportContextImpl();
        MetricRegistry registry = context.getMetricsRegistry();

        // add the webapp filter
        filter = new DefaultSupportMetricsFilter(registry);
        PluginServletFilter.addFilter(filter);

        registry.registerAll(new MemoryUsageGaugeSet());
        registry.registerAll(new GarbageCollectorMetricSet());
        registry.register("file.descriptor.ratio", new FileDescriptorRatioGauge());
        registry.registerAll(new ThreadStatesGaugeSet());
        // add jvm metrics
        jenkinsQueueLength = registry.histogram(name(Queue.class, "size"));
        jenkinsNodeTotalCount = registry.histogram(name(Node.class, "count"));
        jenkinsNodeOnlineCount = registry.histogram(name(Node.class, "online"));
        jenkinsExecutorTotalCount = registry.histogram(name(Executor.class, "count"));
        jenkinsExecutorUsedCount = registry.histogram(name(Executor.class, "in-use"));
        jenkinsBuildDuration = registry.timer(name(Jenkins.class, "builds"));
    }

    private synchronized void updateMetrics() {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkinsQueueLength != null) {
            jenkinsQueueLength.update(jenkins.getQueue().getBuildableItems().size());
        }
        if (jenkinsNodeTotalCount != null || jenkinsNodeOnlineCount != null || jenkinsExecutorTotalCount != null
                || jenkinsExecutorUsedCount != null) {
            int nodeTotal = 0;
            int nodeOnline = 0;
            int executorTotal = 0;
            int executorUsed = 0;
            if (jenkins.getNumExecutors() > 0) {
                nodeTotal++;
                Computer computer = jenkins.toComputer();
                if (computer != null) {
                    if (!computer.isOffline()) {
                        nodeOnline++;
                        for (Executor e : computer.getExecutors()) {
                            executorTotal++;
                            if (!e.isIdle()) {
                                executorUsed++;
                            }
                        }
                    }
                }
            }
            Set<Computer> forRetention = new HashSet<Computer>();
            for (Node node : jenkins.getNodes()) {
                nodeTotal++;
                Computer computer = node.toComputer();
                if (computer == null) {
                    continue;
                }
                if (!computer.isOffline()) {
                    nodeOnline++;
                    for (Executor e : computer.getExecutors()) {
                        executorTotal++;
                        if (!e.isIdle()) {
                            executorUsed++;
                        }
                    }
                }
                forRetention.add(computer);
                getOrCreateTimer(computer);
            }
            for (Map.Entry<Computer, Timer> entry : computerBuildDurations.entrySet()) {
                if (forRetention.contains(entry.getKey())) {
                    continue;
                }
                // purge dead nodes
                getContext().getMetricsRegistry().remove(name(Node.class, "builds", entry.getKey().getName()));
            }
            computerBuildDurations.keySet().retainAll(forRetention);
            if (jenkinsNodeTotalCount != null) {
                jenkinsNodeTotalCount.update(nodeTotal);
            }
            if (jenkinsNodeOnlineCount != null) {
                jenkinsNodeOnlineCount.update(nodeOnline);
            }
            if (jenkinsExecutorTotalCount != null) {
                jenkinsExecutorTotalCount.update(executorTotal);
            }
            if (jenkinsExecutorUsedCount != null) {
                jenkinsExecutorUsedCount.update(executorUsed);
            }
        }

    }

    @NonNull
    public synchronized SupportContextImpl getContext() {
        return context;
    }

    @Override
    public void postInitialize() throws Exception {
        super.postInitialize();
        for (Component component : getComponents()) {
            try {
                component.start(getContext());
            } catch (Throwable t) {
                LogRecord logRecord = new LogRecord(Level.WARNING, "Exception propagated from component: {0}");
                logRecord.setThrown(t);
                logRecord.setParameters(new Object[]{component.getDisplayName()});
                logger.log(logRecord);
            }
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        if (rootLogger != null) {
            rootLogger.removeHandler(handler);
            rootLogger = null;
            handler.close();
        }
        if (filter != null) {
            PluginServletFilter.removeFilter(filter);
        }
        context.shutdown();
        jenkinsQueueLength = null;
        jenkinsNodeTotalCount = null;
        jenkinsNodeOnlineCount = null;
        jenkinsExecutorTotalCount = null;
        jenkinsExecutorUsedCount = null;
        jenkinsBuildDuration = null;
        if (computerBuildDurations != null) {
            computerBuildDurations.clear();
        }
        computerBuildDurations = null;

        super.stop();
    }

    public List<LogRecord> getAllLogRecords(Node node) throws IOException, InterruptedException {
        if (node != null) {
            VirtualChannel channel = node.getChannel();
            if (channel != null) {
                return channel.call(new LogFetcher());
            }
        }
        return Collections.emptyList();
    }

    private synchronized Timer getOrCreateTimer(Computer computer) {
        Timer timer = computerBuildDurations.get(computer);
        if (timer == null) {
            timer = getInstance().getContext().getMetricsRegistry()
                    .timer(name(Node.class, "builds", computer.getName()));
            computerBuildDurations.put(computer, timer);
        }
        return timer;
    }

    public static class LogHolder {
        private static final SupportLogHandler SLAVE_LOG_HANDLER = new SupportLogHandler(256, 2048, 8);
    }

    private static class LogInitializer implements Callable<Void, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private static final Logger ROOT_LOGGER = Logger.getLogger("");
        private final FilePath rootPath;
        private final Level level;

        public LogInitializer(FilePath rootPath, Level level) {
            this.rootPath = rootPath;
            this.level = level;
        }

        public Void call() {
            // avoid double installation of the handler. JNLP slaves can reconnect to the master multiple times
            // and each connection gets a different RemoteClassLoader, so we need to evict them by class name,
            // not by their identity.
            for (Handler h : ROOT_LOGGER.getHandlers()) {
                if (h.getClass().getName().equals(LogHolder.SLAVE_LOG_HANDLER.getClass().getName())) {
                    ROOT_LOGGER.removeHandler(h);
                    try {
                        h.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
            LogHolder.SLAVE_LOG_HANDLER.setLevel(level);
            LogHolder.SLAVE_LOG_HANDLER.setDirectory(new File(rootPath.getRemote(), "support"), "all");
            ROOT_LOGGER.addHandler(LogHolder.SLAVE_LOG_HANDLER);
            return null;
        }

    }

    public static class LogFetcher implements Callable<List<LogRecord>, RuntimeException> {
        private static final long serialVersionUID = 1L;

        public List<LogRecord> call() throws RuntimeException {
            return new ArrayList<LogRecord>(LogHolder.SLAVE_LOG_HANDLER.getRecent());
        }

    }

    public static class LogUpdater implements Callable<Void, RuntimeException> {

        private static final long serialVersionUID = 1L;

        private final Level level;

        public LogUpdater(Level level) {
            this.level = level;
        }

        public Void call() throws RuntimeException {
            LogHolder.SLAVE_LOG_HANDLER.setLevel(level);
            return null;
        }

    }

    @Extension
    public static class ComputerListenerImpl extends ComputerListener {
        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            final Node node = c.getNode();
            if (node instanceof Jenkins) {
                return;
            }
            try {
                final VirtualChannel channel = c.getChannel();
                if (channel != null) {
                    final FilePath rootPath = node.getRootPath();
                    if (rootPath != null) {
                        channel.call(new LogInitializer(rootPath, getLogLevel()));
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(SupportPlugin.class.getName())
                        .log(Level.WARNING, "Could not install root log handler on node: " + c.getName(), e);
            } catch (RuntimeException e) {
                Logger.getLogger(SupportPlugin.class.getName()).log(Level.WARNING,
                        "Could not install root log handler on node: " + c.getName(), e);
            } catch (InterruptedException e) {
                Logger.getLogger(SupportPlugin.class.getName()).log(Level.WARNING,
                        "Could not install root log handler on node: " + c.getName(), e);
            }
        }
    }

    @Extension
    public static class PeriodicWorkImpl extends PeriodicWork {

        private Thread thread;

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(15);
        }

        @Override
        protected synchronized void doRun() throws Exception {
            final SupportPlugin plugin = getInstance();
            if (plugin == null) {
                return;
            }
            plugin.updateMetrics();
            if (nextBundleWrite.get() < System.currentTimeMillis()) {
                if (thread != null && thread.isAlive()) {
                    logger.log(Level.INFO, "Periodic bundle generating thread is still running. Execution aborted.");
                    return;
                }
                try {
                    thread = new Thread(new Runnable() {
                        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                                value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"},
                                justification = "Best effort"
                        )
                        public void run() {
                            nextBundleWrite.set(System.currentTimeMillis() + TimeUnit2.HOURS.toMillis(1));
                            clearRequesterAuthentication();
                            SecurityContext old = ACL.impersonate(ACL.SYSTEM);
                            try {
                                File bundleDir = new File(Jenkins.getInstance().getRootDir(), "support");
                                if (!bundleDir.exists()) {
                                    if (!bundleDir.mkdirs()) {
                                        return;
                                    }
                                }
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
                                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

                                final String bundlePrefix = "support";
                                File file = new File(bundleDir,
                                        bundlePrefix + "_" + dateFormat.format(new Date()) + ".zip");
                                FileOutputStream fos = null;
                                try {
                                    fos = new FileOutputStream(file);
                                    writeBundle(fos);
                                } finally {
                                    IOUtils.closeQuietly(fos);
                                }
                                File[] files = bundleDir.listFiles(new FilenameFilter() {
                                    public boolean accept(File dir, String name) {
                                        return name.startsWith(bundlePrefix) && name.endsWith(".zip");
                                    }
                                });
                                long pivot = System.currentTimeMillis();
                                for (long l = 1; l * 2 > 0; l *= 2) {
                                    boolean seen = false;
                                    for (File f : files) {
                                        if (!f.isFile() || f == file) {
                                            continue;
                                        }
                                        long age = pivot - f.lastModified();
                                        if (l <= age && age < l * 2) {
                                            if (seen) {
                                                f.delete();
                                            } else {
                                                seen = true;
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                logger.log(Level.WARNING, "Could not save support bundle", t);
                            } finally {
                                SecurityContextHolder.setContext(old);
                            }
                        }
                    }, SupportPlugin.class.getSimpleName() + " periodic bundle generator");
                    thread.start();
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Periodic bundle generating thread failed with error", t);
                }
            }
        }

    }

    @Extension
    public static class RunListenerImpl extends RunListener<Run> {
        private Map<Run, List<Timer.Context>> contexts = new HashMap<Run, List<Timer.Context>>();

        @Override
        public synchronized void onStarted(Run run, TaskListener listener) {
            List<Timer.Context> contextList = new ArrayList<Timer.Context>();
            contextList.add(getInstance().jenkinsBuildDuration.time());
            Executor executor = run.getExecutor();
            if (executor != null) {
                Computer computer = executor.getOwner();
                Timer timer = getInstance().getOrCreateTimer(computer);
                contextList.add(timer.time());
            }
            contexts.put(run, contextList);
        }

        @Override
        public synchronized void onCompleted(Run run, TaskListener listener) {
            List<Timer.Context> contextList = contexts.remove(run);
            if (contextList != null) {
                for (Timer.Context context : contextList) {
                    context.stop();
                }
            }
        }
    }

    @Extension
    public static class GlobalConfigurationImpl extends GlobalConfiguration {

        public boolean isSelectable() {
            return Jenkins.getInstance().getDescriptorList(SupportProvider.class).size() > 1;
        }

        public SupportProvider getSupportProvider() {
            return getInstance().getSupportProvider();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            if (json.has("supportProvider")) {
                try {
                    getInstance().setSupportProvider(req.bindJSON(SupportProvider.class,
                            json.getJSONObject("supportProvider")));
                } catch (IOException e) {
                    throw new FormException(e, "supportProvider");
                }
            }
            return true;
        }
    }

}
