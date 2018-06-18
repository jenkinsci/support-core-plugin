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
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.jenkins.support.filter.FilteredOutputStream;
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.util.IgnoreCloseOutputStream;
import com.cloudbees.jenkins.support.util.OutputStreamSelector;
import com.codahale.metrics.Histogram;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Main;
import hudson.Plugin;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.slaves.ComputerListener;
import jenkins.metrics.impl.JenkinsMetricProviderImpl;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Main entry point for the support plugin.
 *
 * @author Stephen Connolly
 */
public class SupportPlugin extends Plugin {

    /**
     * How long remote operations can block support bundle generation for.
     */
    public static final int REMOTE_OPERATION_TIMEOUT_MS =
            Integer.getInteger(SupportPlugin.class.getName() + ".REMOTE_OPERATION_TIMEOUT_MS", 500);

    /**
     * How long remote operations fallback caching can wait for
     */
    public static final int REMOTE_OPERATION_CACHE_TIMEOUT_SEC =
            Integer.getInteger(SupportPlugin.class.getName() + ".REMOTE_OPERATION_CACHE_TIMEOUT_SEC", 300);

    /**
     * How often automatic support bundles should be collected. Should be {@code 1} unless you have very good reason
     * to use a different period. {@code 0} disables bundle generation and {@code 24} is the longest period permitted.
     */
    public static final int AUTO_BUNDLE_PERIOD_HOURS =
            Math.max(Math.min(24, Integer.getInteger(SupportPlugin.class.getName() + ".AUTO_BUNDLE_PERIOD_HOURS", 1)), 0);

    public static final PermissionGroup SUPPORT_PERMISSIONS =
            new PermissionGroup(SupportPlugin.class, Messages._SupportPlugin_PermissionGroup());

    public static final Permission CREATE_BUNDLE =
            new Permission(SUPPORT_PERMISSIONS, "DownloadBundle", Messages._SupportPlugin_CreateBundle(),
                    Jenkins.ADMINISTER, PermissionScope.JENKINS);
    private static final ThreadLocal<Authentication> requesterAuthentication = new InheritableThreadLocal<>();
    private static final AtomicLong nextBundleWrite = new AtomicLong(Long.MIN_VALUE);
    private static final Logger logger = Logger.getLogger(SupportPlugin.class.getName());
    public static final String SUPPORT_DIRECTORY_NAME = "support";
    private transient final SupportLogHandler handler = new SupportLogHandler(256, 2048, 8);

    private transient SupportContextImpl context = null;
    private transient Logger rootLogger;
    private transient WeakHashMap<Node, List<LogRecord>> logRecords;

    private SupportProvider supportProvider;

    /**
     * class names of {@link Component}
     */
    private Set<String> excludedComponents;

    public SupportPlugin() {
        super();
        handler.setLevel(getLogLevel());
        handler.setDirectory(getRootDirectory(), "all");
    }

    public SupportProvider getSupportProvider() {
        if (supportProvider == null) {
            // if this is not set, pick the first one that we can get our hands on
            for (Descriptor<SupportProvider> d : Jenkins.get().getDescriptorList(SupportProvider.class)) {
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

    /**
     * Working directory that the support-core plugin uses to write out files.
     *
     * @return the wrking directory that the support-core plugin uses to write out files.
     */
    public static File getRootDirectory() {
        return new File(Jenkins.get().getRootDir(), SUPPORT_DIRECTORY_NAME);
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
        return excludedComponents != null ? excludedComponents : Collections.emptySet();
    }

    /**
     * Sets the ids of the components to be excluded.
     *
     * @param excludedComponents Component Ids (by default class names) to exclude.
     * @throws IOException if an error occurs while saving the configuration.
     * @see Component#getId
     */
    public void setExcludedComponents(Set<String> excludedComponents) throws IOException {
        this.excludedComponents = excludedComponents;
        save();
    }

    public Histogram getJenkinsExecutorTotalCount() {
        return JenkinsMetricProviderImpl.instance().getJenkinsExecutorTotalCount();
    }

    public Histogram getJenkinsExecutorUsedCount() {
        return JenkinsMetricProviderImpl.instance().getJenkinsExecutorUsedCount();
    }

    public Histogram getJenkinsNodeOnlineCount() {
        return JenkinsMetricProviderImpl.instance().getJenkinsNodeOnlineCount();
    }

    public Histogram getJenkinsNodeTotalCount() {
        return JenkinsMetricProviderImpl.instance().getJenkinsNodeTotalCount();
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
        for (Node n : Jenkins.get().getNodes()) {
            Computer c = n.toComputer();
            if (c == null) {
                continue;
            }
            VirtualChannel channel = c.getChannel();
            if (channel != null) {
                try {
                    channel.callAsync(new LogUpdater(level));
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static SupportPlugin getInstance() {
        return Jenkins.get().getPlugin(SupportPlugin.class);
    }

    public static ExtensionList<Component> getComponents() {
        return Jenkins.get().getExtensionList(Component.class);
    }

    public static void writeBundle(OutputStream outputStream) throws IOException {
        writeBundle(outputStream, getComponents());
    }

    public static void writeBundle(OutputStream outputStream, final List<Component> components) throws IOException {
        StringBuilder manifest = new StringBuilder();
        StringWriter errors = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(errors);
        appendManifestHeader(manifest);
        List<Content> contents = appendManifestContents(manifest, errorWriter, components);
        contents.add(new StringContent("manifest.md", manifest.toString()));
        try {
            try (BulkChange change = new BulkChange(ContentMappings.get());
                 ZipArchiveOutputStream binaryOut = new ZipArchiveOutputStream(new BufferedOutputStream(outputStream, 16384))) {
                Optional<ContentFilter> maybeFilter = getContentFilter();
                Optional<FilteredOutputStream> maybeFilteredOut = maybeFilter.map(filter -> new FilteredOutputStream(binaryOut, filter));
                OutputStream textOut = maybeFilteredOut.map(OutputStream.class::cast).orElse(binaryOut);
                OutputStreamSelector selector = new OutputStreamSelector(() -> binaryOut, () -> textOut);
                IgnoreCloseOutputStream unfilteredOut = new IgnoreCloseOutputStream(binaryOut);
                IgnoreCloseOutputStream filteredOut = new IgnoreCloseOutputStream(selector);
                for (Content content : contents) {
                    if (content == null) {
                        continue;
                    }
                    final String name = maybeFilter.map(filter -> filter.filter(content.getName())).orElseGet(content::getName);
                    final ZipArchiveEntry entry = new ZipArchiveEntry(name);
                    entry.setTime(content.getTime());
                    try {
                        binaryOut.putArchiveEntry(entry);
                        binaryOut.flush();
                        OutputStream out = content.shouldBeFiltered() ? filteredOut : unfilteredOut;
                        content.writeTo(out);
                        out.flush();
                    } catch (Throwable e) {
                        String msg = "Could not attach ''" + name + "'' to support bundle";
                        logger.log(Level.WARNING, msg, e);
                        errorWriter.println(msg);
                        errorWriter.println("-----------------------------------------------------------------------");
                        errorWriter.println();
                        SupportLogFormatter.printStackTrace(e, errorWriter);
                        errorWriter.println();
                    } finally {
                        maybeFilteredOut.ifPresent(FilteredOutputStream::reset);
                        selector.reset();
                        binaryOut.closeArchiveEntry();
                    }
                }
                errorWriter.close();
                String errorContent = errors.toString();
                if (StringUtils.isNotBlank(errorContent)) {
                    try {
                        binaryOut.putArchiveEntry(new ZipArchiveEntry("manifest/errors.txt"));
                        textOut.write(errorContent.getBytes(StandardCharsets.UTF_8));
                        textOut.flush();
                        binaryOut.closeArchiveEntry();
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not write manifest/errors.txt to zip archive", e);
                    }
                }
                binaryOut.flush();
                change.commit();
            }
        } finally {
            outputStream.flush();
        }
    }

    private static Optional<ContentFilter> getContentFilter() throws IOException {
        ContentFilters filters = ContentFilters.get();
        if (filters.isEnabled()) {
            ContentFilter filter = ContentFilter.ALL;
            ContentMappings mappings = ContentMappings.get();
            try (BulkChange change = new BulkChange(mappings)) {
                mappings.reload();
                filter.reload();
                change.commit();
            }
            return Optional.of(filter);
        }
        return Optional.empty();
    }

    private static void appendManifestHeader(StringBuilder manifest) {
        SupportPlugin plugin = SupportPlugin.getInstance();
        SupportProvider supportProvider = plugin == null ? null : plugin.getSupportProvider();
        String bundleName =
                (supportProvider == null ? "Support" : supportProvider.getDisplayName()) + " Bundle Manifest";
        manifest.append(bundleName)
                .append('\n')
                .append(StringUtils.repeat("=", bundleName.length()))
                .append("\n\n");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        manifest.append("Generated on ")
                .append(f.format(new Date()))
                .append("\n\n");
    }

    private static List<Content> appendManifestContents(StringBuilder manifest, PrintWriter errors, List<Component> components) {
        manifest.append("Requested components:\n\n");
        ContentContainer contents = new ContentContainer();
        for (Component component : components) {
            try {
                manifest.append("  * ").append(component.getDisplayName()).append("\n\n");
                component.addContents(contents);
                Set<String> names = contents.getLatestNames();
                for (String name : names) {
                    manifest.append("      - `").append(name).append("`\n\n");
                }
            } catch (Throwable e) {
                String displayName;
                try {
                    displayName = component.getDisplayName();
                } catch (Throwable ignored) {
                    // be very defensive
                    displayName = component.getClass().getName();
                }
                String msg = "Could not get content from " + displayName + " for support bundle";
                logger.log(Level.WARNING, msg, e);
                errors.println(msg);
                errors.println("-----------------------------------------------------------------------");
                errors.println();
                SupportLogFormatter.printStackTrace(e, errors);
                errors.println();
            }
        }
        return contents.contents;
    }

    private static class ContentContainer extends Container {
        private final List<Content> contents = new ArrayList<>();
        private final Set<String> names = new TreeSet<>();

        @Override
        public void add(Content content) {
            if (content != null) {
                contents.add(content);
                names.add(content.getName());
            }
        }

        private Set<String> getLatestNames() {
            Set<String> copy = new TreeSet<>(names);
            names.clear();
            return copy;
        }
    }

    public List<LogRecord> getAllLogRecords() {
        return handler.getRecent();
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void loadConfig() throws IOException {
        SupportPlugin instance = getInstance();
        if (instance != null)
            instance.load();
    }

    private static final boolean logStartupPerformanceIssues = Boolean.getBoolean(SupportPlugin.class.getCanonicalName() + ".threadDumpStartup");
    private static final int secondsPerThreadDump = Integer.getInteger(SupportPlugin.class.getCanonicalName() + ".secondsPerTD", 60);

    @Deprecated
    @Restricted(NoExternalUse.class)
    public static void completedMilestones() throws IOException {
        // Do nothing
    }

    @Initializer(after = InitMilestone.STARTED)
    public static void threadDumpStartup() throws Exception {
        if (!logStartupPerformanceIssues) return;
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        final File f = new File(getRootDirectory(), "/startup-threadDump" + dateFormat.format(new Date()) + ".txt");
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Thread t = new Thread("Support core plugin startup diagnostics") {
            @Override
            public void run() {
                try {
                    while (true) {
                        final Jenkins jenkins = Jenkins.getInstanceOrNull();
                        if (jenkins == null || jenkins.getInitLevel() != InitMilestone.COMPLETED) {
                            continue;
                        }
                        try (PrintStream ps = new PrintStream(new FileOutputStream(f, true), false, "UTF-8")) {
                            ps.println("=== Thread dump at " + new Date() + " ===");
                            ThreadDumps.threadDump(ps);
                            // Generate a thread dump every few seconds/minutes
                            ps.flush();
                            TimeUnit.SECONDS.sleep(secondsPerThreadDump);
                        } catch (FileNotFoundException | UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        };
        t.start();
    }


    @Override
    public synchronized void start() throws Exception {
        super.start();
        rootLogger = Logger.getLogger("");
        rootLogger.addHandler(handler);
        context = new SupportContextImpl();
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
        context.shutdown();

        super.stop();
    }

    public List<LogRecord> getAllLogRecords(final Node node) throws IOException, InterruptedException {
        if (node != null) {
            VirtualChannel channel = node.getChannel();
            if (channel != null) {
                final Future<List<LogRecord>> future = channel.callAsync(new LogFetcher());
                try {
                    return future.get(REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (ExecutionException e) {
                    final LogRecord lr = new LogRecord(Level.WARNING, "Could not retrieve remote log records");
                    lr.setThrown(e);
                    return Collections.singletonList(lr);
                } catch (TimeoutException e) {
                    Computer.threadPoolForRemoting.submit(() -> {
                        List<LogRecord> records;
                        try {
                            records = future.get(REMOTE_OPERATION_CACHE_TIMEOUT_SEC, TimeUnit.SECONDS);
                        } catch (InterruptedException e1) {
                            final LogRecord lr =
                                    new LogRecord(Level.WARNING, "Could not retrieve remote log records");
                            lr.setThrown(e1);
                            records = Collections.singletonList(lr);
                        } catch (ExecutionException e1) {
                            final LogRecord lr =
                                    new LogRecord(Level.WARNING, "Could not retrieve remote log records");
                            lr.setThrown(e1);
                            records = Collections.singletonList(lr);
                        } catch (TimeoutException e1) {
                            final LogRecord lr =
                                    new LogRecord(Level.WARNING, "Could not retrieve remote log records");
                            lr.setThrown(e1);
                            records = Collections.singletonList(lr);
                            future.cancel(true);
                        }
                        synchronized (SupportPlugin.this) {
                            if (logRecords == null) {
                                logRecords = new WeakHashMap<>();
                            }
                            logRecords.put(node, records);
                        }
                    });
                    synchronized (this) {
                        if (logRecords != null) {
                            List<LogRecord> result = logRecords.get(node);
                            if (result != null) {
                                result = new ArrayList<>(result);
                                final LogRecord lr = new LogRecord(Level.WARNING, "Using cached remote log records");
                                lr.setThrown(e);
                                result.add(lr);
                                return result;
                            }
                        } else {
                            final LogRecord lr = new LogRecord(Level.WARNING, "No previous cached remote log records");
                            lr.setThrown(e);
                            return Collections.singletonList(lr);
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns the full bundle name.
     *
     * @return the full bundle name.
     */
    @NonNull
    public static String getBundleFileName() {
        StringBuilder filename = new StringBuilder();
        filename.append(getBundlePrefix());

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        filename.append("_").append(dateFormat.format(new Date()));

        filename.append(".zip");
        return filename.toString();
    }

    /**
     * Returns the prefix of the bundle name.
     *
     * @return the prefix of the bundle name.
     */
    private static String getBundlePrefix() {
        String filename = "support"; // default bundle filename
        final SupportPlugin instance = getInstance();
        if (instance != null) {
            SupportProvider supportProvider = instance.getSupportProvider();
            if (supportProvider != null) {
                // let the provider name it
                filename = supportProvider.getName();
            }
        }
        final String instanceType = BundleNameInstanceTypeProvider.getInstance().getInstanceType();
        if (StringUtils.isNotBlank(instanceType)) {
            filename = filename + "_" + instanceType;
        }
        return filename;
    }

    public static class LogHolder {
        private static final SupportLogHandler SLAVE_LOG_HANDLER = new SupportLogHandler(256, 2048, 8);
    }

    private static class LogInitializer extends MasterToSlaveCallable<Void, RuntimeException> {
        private static final long serialVersionUID = 1L;
        private static final Logger ROOT_LOGGER = Logger.getLogger("");
        private final FilePath rootPath;
        private final Level level;

        public LogInitializer(FilePath rootPath, Level level) {
            this.rootPath = rootPath;
            this.level = level;
        }

        public Void call() {
            // avoid double installation of the handler. JNLP agents can reconnect to the master multiple times
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
            LogHolder.SLAVE_LOG_HANDLER.setDirectory(new File(rootPath.getRemote(), SUPPORT_DIRECTORY_NAME), "all");
            ROOT_LOGGER.addHandler(LogHolder.SLAVE_LOG_HANDLER);
            return null;
        }

    }

    public static class LogFetcher extends MasterToSlaveCallable<List<LogRecord>, RuntimeException> {
        private static final long serialVersionUID = 1L;

        public List<LogRecord> call() throws RuntimeException {
            return new ArrayList<>(LogHolder.SLAVE_LOG_HANDLER.getRecent());
        }

    }

    public static class LogUpdater extends MasterToSlaveCallable<Void, RuntimeException> {

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
                        channel.callAsync(new LogInitializer(rootPath, getLogLevel()));
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(SupportPlugin.class.getName())
                        .log(Level.WARNING, "Could not install root log handler on node: " + c.getName(), e);
            } catch (RuntimeException e) {
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
            if (Main.isUnitTest) {
                return;
            }
            final SupportPlugin plugin = getInstance();
            if (plugin == null) {
                return;
            }
            if (nextBundleWrite.get() < System.currentTimeMillis() && AUTO_BUNDLE_PERIOD_HOURS > 0) {
                if (thread != null && thread.isAlive()) {
                    logger.log(Level.INFO, "Periodic bundle generating thread is still running. Execution aborted.");
                    return;
                }
                try {
                    thread = new Thread(() -> {
                        nextBundleWrite.set(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(AUTO_BUNDLE_PERIOD_HOURS));
                        thread.setName(String.format("%s periodic bundle generator: since %s",
                                SupportPlugin.class.getSimpleName(), new Date()));
                        clearRequesterAuthentication();
                        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
                        try {
                            File bundleDir = getRootDirectory();
                            if (!bundleDir.exists()) {
                                if (!bundleDir.mkdirs()) {
                                    return;
                                }
                            }

                            File file = new File(bundleDir, SupportPlugin.getBundleFileName());
                            thread.setName(String.format("%s periodic bundle generator: writing %s since %s",
                                    SupportPlugin.class.getSimpleName(), file.getName(), new Date()));
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                writeBundle(fos);
                            }
                            cleanupOldBundles(bundleDir, file);
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Could not save support bundle", t);
                        } finally {
                            SecurityContextHolder.setContext(old);
                        }
                    }, SupportPlugin.class.getSimpleName() + " periodic bundle generator");
                    thread.start();
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Periodic bundle generating thread failed with error", t);
                }
            }
        }

        @edu.umd.cs.findbugs.annotations.SuppressWarnings(
                value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "IS2_INCONSISTENT_SYNC"},
                justification = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE=Best effort, " +
                        "IS2_INCONSISTENT_SYNC=only called from an already synchronized method"
        )
        private void cleanupOldBundles(File bundleDir, File justGenerated) {
            thread.setName(String.format("%s periodic bundle generator: tidying old bundles since %s",
                    SupportPlugin.class.getSimpleName(), new Date()));
            File[] files = bundleDir.listFiles((dir, name) -> name.endsWith(".zip"));
            if (files == null) {
                logger.log(Level.WARNING, "Something is wrong: {0} does not exist or there was an IO issue.",
                        bundleDir.getAbsolutePath());
                return;
            }
            long pivot = System.currentTimeMillis();
            for (long l = 1; l * 2 > 0; l *= 2) {
                boolean seen = false;
                for (File f : files) {
                    if (!f.isFile() || f == justGenerated) {
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
        }

    }

    @Extension
    public static class GlobalConfigurationImpl extends GlobalConfiguration {

        public boolean isSelectable() {
            return Jenkins.get().getDescriptorList(SupportProvider.class).size() > 1;
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
