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

import static com.cloudbees.jenkins.support.SupportAction.SYNC_SUPPORT_BUNDLE;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.ComponentVisitor;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.api.SupportProviderDescriptor;
import com.cloudbees.jenkins.support.api.UnfilteredStringContent;
import com.cloudbees.jenkins.support.config.SupportAutomatedBundleConfiguration;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.FilteredOutputStream;
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.util.CallAsyncWrapper;
import com.cloudbees.jenkins.support.util.IgnoreCloseOutputStream;
import com.cloudbees.jenkins.support.util.OutputStreamSelector;
import com.codahale.metrics.Histogram;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Main;
import hudson.Plugin;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.model.TaskListener;
import hudson.remoting.ChannelClosedException;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import hudson.slaves.ComputerListener;
import hudson.triggers.SafeTimerTask;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
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
import java.util.function.DoubleConsumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import jenkins.metrics.impl.JenkinsMetricProviderImpl;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.Authentication;

/**
 * Main entry point for the support plugin.
 *
 * @author Stephen Connolly
 */
public class SupportPlugin extends Plugin {

    private static final Logger LOGGER = Logger.getLogger(SupportPlugin.class.getName());

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
    public static final int AUTO_BUNDLE_PERIOD_HOURS = Math.max(
            Math.min(24, Integer.getInteger(SupportPlugin.class.getName() + ".AUTO_BUNDLE_PERIOD_HOURS", 1)), 0);

    public static final PermissionGroup SUPPORT_PERMISSIONS =
            new PermissionGroup(SupportPlugin.class, Messages._SupportPlugin_PermissionGroup());

    /**
     * @deprecated not used anymore as the usage has now been limited to {@link Jenkins#ADMINISTER}
     */
    @Deprecated
    public static final Permission CREATE_BUNDLE = new Permission(
            SUPPORT_PERMISSIONS,
            "DownloadBundle",
            Messages._SupportPlugin_CreateBundle(),
            Jenkins.ADMINISTER,
            PermissionScope.JENKINS);

    private static final AtomicLong nextBundleWrite = new AtomicLong(Long.MIN_VALUE);
    private static final Logger logger = Logger.getLogger(SupportPlugin.class.getName());
    public static final String SUPPORT_DIRECTORY_NAME = "support";
    private final transient SupportLogHandler handler = new SupportLogHandler(256, 2048, 8);

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
        handler.setDirectory(getLogsDirectory(), "all");
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void migrateExistingLogs() {
        File rootDirectory = getRootDirectory();
        File[] files = rootDirectory.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".log")) {
                    Path p = f.toPath();
                    try {
                        Files.move(p, getLogsDirectory().toPath().resolve(p.getFileName()));
                        LOGGER.log(Level.INFO, "Moved " + p + " to " + getLogsDirectory());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, e, () -> "Unable to move " + p + " to " + getLogsDirectory());
                    }
                }
            }
        }
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
     * @return the working directory that the support-core plugin uses to write out files.
     */
    public static File getRootDirectory() {
        return new File(Jenkins.get().getRootDir(), SUPPORT_DIRECTORY_NAME);
    }

    /**
     * Working directory that the support-core plugin uses to write out log files.
     *
     * @return the working directory that the support-core plugin uses to write out log files.
     */
    public static File getLogsDirectory() {
        return new File(SafeTimerTask.getLogsRoot(), SUPPORT_DIRECTORY_NAME);
    }

    /**
     * @deprecated Check permissions directly.
     */
    @Deprecated
    public static Authentication getRequesterAuthentication() {
        return Jenkins.getAuthentication2();
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
        ExtensionList list = ExtensionList.create(Jenkins.get(), NonExistentComponent.class);

        if (list.isEmpty()) {
            List<Component> applicableComponents = Jenkins.get().getExtensionList(Component.class).stream()
                    .filter(component -> component.isApplicable(Jenkins.class))
                    .collect(Collectors.toList());

            list.addAll(applicableComponents);
        }

        return list;
    }

    private abstract static class NonExistentComponent extends Component {}

    /**
     * Generate a bundle for all components that are selected in the Global Configuration.
     *
     * @param outputStream an {@link OutputStream}
     * @throws IOException if an error occurs while generating the bundle.
     */
    @Deprecated
    public static void writeBundle(OutputStream outputStream) throws IOException {
        writeBundle(outputStream, SupportAutomatedBundleConfiguration.get().getComponents());
    }

    /**
     * Generate a bundle for all components that are selected in the Global Configuration.
     *
     * @param outputStream an {@link OutputStream}
     * @param components a list of {@link Component} to include in the bundle
     * @throws IOException if an error occurs while generating the bundle.
     */
    public static void writeBundle(OutputStream outputStream, final List<? extends Component> components)
            throws IOException {
        writeBundle(
                outputStream,
                components,
                new ComponentVisitor() {
                    @Override
                    public <T extends Component> void visit(Container container, T component) {
                        component.addContents(container);
                    }
                },
                null,
                true);
    }

    /**
     * Generate a bundle for the specified synchronous components.
     * This is useful when generating a support bundle asynchronously. There are some components that can't be generated
     * asynchronously as they need some context from the request. So these components must be first processed synchronously
     * and then the remaining components can be processed asynchronously.
     *
     * @param components a list of synchronous {@link Component} to include in the bundle
     * @throws IOException if an error occurs while generating the bundle.
     */
    static void writeBundleForSyncComponents(OutputStream outputStream, final List<? extends Component> components)
            throws IOException {
        writeBundle(
                outputStream,
                components,
                new ComponentVisitor() {
                    @Override
                    public <T extends Component> void visit(Container container, T component) {
                        component.addContents(container);
                    }
                },
                null,
                false);
    }

    /**
     * Generate a bundle for the specified components.
     *
     * @param components a list of {@link Component} to include in the bundle
     * @param progressCallback a callback to report progress back to the UI see ProgressiveRendering.progress
     * @param outputPath the path with the support bundle will be created in the cases of async generations
     * @throws IOException if an error occurs while generating the bundle.
     */
    static void writeBundle(
            OutputStream outputStream,
            final List<? extends Component> components,
            DoubleConsumer progressCallback,
            Path outputPath)
            throws IOException {
        writeBundle(
                outputStream,
                components,
                new ComponentVisitor() {
                    private final int totalComponents = components.size();
                    private int currentIteration = 0;

                    @Override
                    public <T extends Component> void visit(Container container, T component) {
                        if (component.canBeGeneratedAsync()) {
                            component.addContents(container);
                        }
                        progressCallback.accept((currentIteration++) / (double) totalComponents);
                    }
                },
                outputPath,
                true);
    }

    /**
     * Generate a bundle for all components that are selected in the Global Configuration.
     *
     * @param outputStream an {@link OutputStream}
     * @param components a list of {@link Component} to include in the bundle
     * @param componentConsumer a {@link ComponentVisitor}
     * @param outputPath the path with the support bundle will be created in the cases of async generations
     *                   set this to null, if generating support bundle synchronously
     * @throws IOException if an error occurs while generating the bundle.
     */
    public static void writeBundle(
            OutputStream outputStream,
            final List<? extends Component> components,
            ComponentVisitor componentConsumer,
            Path outputPath,
            boolean addManifest)
            throws IOException {
        StringBuilder manifest = new StringBuilder();
        StringWriter errors = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(errors);

        try {
            try (BulkChange change = new BulkChange(ContentFilter.bulkChangeTarget());
                    CountingOutputStream countingOs = new CountingOutputStream(outputStream);
                    ZipOutputStream binaryOut = new ZipOutputStream(new BufferedOutputStream(countingOs, 16384))) {
                ContentFilter filter = getDefaultContentFilter(true);

                // Generate the content of the manifest.md going through all the components which will be included. It
                // also returns the contents to include. We pass a filter to filter the names written in the manifest
                appendManifestHeader(manifest);
                long startTime = System.currentTimeMillis();
                List<Content> contents =
                        appendManifestContents(manifest, errorWriter, components, componentConsumer, filter);
                LOGGER.log(
                        Level.FINE,
                        "Took " + (System.currentTimeMillis() - startTime) + "ms to process all components");
                if (addManifest) {
                    contents.add(new UnfilteredStringContent("manifest.md", manifest.toString()));
                }

                FilteredOutputStream textOut = new FilteredOutputStream(binaryOut, filter);
                OutputStreamSelector selector = new OutputStreamSelector(() -> binaryOut, () -> textOut);
                IgnoreCloseOutputStream unfilteredOut = new IgnoreCloseOutputStream(binaryOut);
                IgnoreCloseOutputStream filteredOut = new IgnoreCloseOutputStream(selector);
                boolean entryCreated = false;
                startTime = System.currentTimeMillis();
                long startSize = countingOs.getByteCount();
                for (Content content : contents) {
                    if (content == null) {
                        continue;
                    }
                    LOGGER.log(Level.FINE, "Start writing support content " + content.getClass());
                    long contentStartTime = System.currentTimeMillis();
                    long contentStartSize = countingOs.getByteCount();
                    final String name = getNameFiltered(filter, content.getName(), content.getFilterableParameters());

                    try {
                        final ZipEntry entry = new ZipEntry(name);
                        entry.setTime(content.getTime());
                        binaryOut.putNextEntry(entry);
                        entryCreated = true;
                        binaryOut.flush();
                        OutputStream out = content.shouldBeFiltered() ? filteredOut : unfilteredOut;
                        if (content instanceof PrefilteredContent) {
                            ((PrefilteredContent) content).writeTo(out, filter);
                        } else {
                            content.writeTo(out);
                        }
                        out.flush();
                    } catch (Throwable e) {
                        String msg = "Could not attach ''" + name + "'' to support bundle";
                        logger.log(e instanceof ChannelClosedException ? Level.FINE : Level.WARNING, msg, e);
                        errorWriter.println(msg);
                        errorWriter.println("-----------------------------------------------------------------------");
                        errorWriter.println();
                        Functions.printStackTrace(e, errorWriter);
                        errorWriter.println();
                    } finally {
                        textOut.reset();
                        selector.reset();
                        if (entryCreated) {
                            binaryOut.closeEntry();
                            entryCreated = false;
                        }
                        LOGGER.log(
                                Level.FINE,
                                "Took " + (System.currentTimeMillis() - contentStartTime) + "ms" + " and generated "
                                        + (countingOs.getByteCount() - contentStartSize) + " bytes"
                                        + " to write content "
                                        + name);
                    }
                }

                // process for async components
                if (outputPath != null) {
                    try {
                        File zipFile = outputPath.resolve(SYNC_SUPPORT_BUNDLE).toFile();
                        if (zipFile.exists()) {
                            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                                ZipEntry entry;
                                while ((entry = zis.getNextEntry()) != null) {
                                    binaryOut.putNextEntry(entry);
                                    zis.transferTo(binaryOut);
                                }
                            }
                        } else {
                            LOGGER.log(Level.FINE, "No sync component to process");
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error while processing sync components in async mode", e);
                    }
                }

                LOGGER.log(
                        Level.FINE,
                        "Took " + (System.currentTimeMillis() - startTime) + "ms" + " and generated "
                                + (countingOs.getByteCount() - startSize) + " bytes" + " to process all contents");
                errorWriter.close();
                String errorContent = errors.toString();
                if (StringUtils.isNotBlank(errorContent)) {
                    try {
                        binaryOut.putNextEntry(new ZipEntry("manifest/errors.txt"));
                        entryCreated = true;
                        textOut.write(errorContent.getBytes(StandardCharsets.UTF_8));
                        textOut.flush();
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Could not write manifest/errors.txt to zip archive", e);
                    } finally {
                        if (entryCreated) {
                            binaryOut.closeEntry();
                        }
                    }
                }
                binaryOut.flush();
                change.commit();
            }
        } finally {
            outputStream.flush();
        }
    }

    /**
     * Filter the name of a content depending on the filterableParameters in the name that need to be replaced.
     * @param contentFilter an Optional with a {@link ContentFilter} or not
     * @param name the name of the content to be filtered
     * @param filterableParameters filterableParameters in the name to be filtered. If null, no filter takes place to avoid corruption
     * @return the name filtered
     */
    static String getNameFiltered(ContentFilter contentFilter, String name, String[] filterableParameters) {
        String filteredName;

        if (filterableParameters != null) {
            // Filter each token or return the token depending on whether the filter is active or not
            Object[] replacedParameters = Arrays.stream(filterableParameters)
                    .map(contentFilter::filter)
                    .toArray(String[]::new);

            // Replace each placeholder {0}, {1} in the name, with the replaced token
            filteredName = MessageFormat.format(name, replacedParameters);
        } else {
            // Previous behavior was filter all the name, but it could end up in having a corrupted bundle. So we expect
            // implementors to use the appropriate constructor of Content.
            // filteredName = contentFilter.filter(name);
            filteredName = name;
        }

        return filteredName;
    }

    /**
     * Get the filter to be used in an {@link Optional} just in case.
     * @return the filter.
     *
     * @deprecated use getDefaultContentFilter()
     */
    @NonNull
    @Deprecated
    public static Optional<ContentFilter> getContentFilter() {
        return Optional.of(getDefaultContentFilter(true));
    }

    /**
     * Get the filter to be used in an {@link Optional} just in case.
     * @param ensureLoaded true to ensure that the filter is loaded. Not necessary if {@link ContentFilter#reload()} is
     *                     done explicitly by the caller.
     * @return the filter.
     * @deprecated use getDefaultContentFilter(ensureLoaded)
     */
    @Deprecated
    public static Optional<ContentFilter> getContentFilter(boolean ensureLoaded) {
        ContentFilters filters = ContentFilters.get();
        if (filters.isEnabled()) {
            return Optional.of(getDefaultContentFilter(ensureLoaded));
        }
        return Optional.empty();
    }

    /**
     * Get the current {@link ContentFilter}.
     *
     * @return the {@link ContentFilter} or a pass-through filter if anonymization is off
     */
    @NonNull
    public static ContentFilter getDefaultContentFilter() {
        return getDefaultContentFilter(true);
    }

    /**
     * Get the filter to be used.
     * @param ensureLoaded true to ensure that the filter is loaded. Not necessary if {@link ContentFilter#reload()} is
     *                     done explicitly by the caller.
     * @return the filter.
     */
    @NonNull
    public static ContentFilter getDefaultContentFilter(boolean ensureLoaded) {
        ContentFilters filters = ContentFilters.get();
        if (filters.isEnabled()) {
            ContentFilter filter = ContentFilter.ALL;
            if (ensureLoaded) {
                try {
                    ContentFilter.reloadAndSaveMappings(filter);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to reload filter and save mappings", e);
                }
            }
            return filter;
        }
        return ContentFilter.NONE;
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
        manifest.append("Generated on ").append(f.format(new Date())).append("\n\n");
    }

    /**
     * Populate the manifest with the content names which are going to be added to the bundle. In addition, it returns
     * the list of content added. To be able to add the names in the manifest.md properly filtered, the current set of
     * filters are specified.
     * @param manifest where to append the names of the contents added to the bundle
     * @param errors where to print error messages
     * @param components components to add their contents to the bundle
     * @param componentVisitor visitor to be used when walking through components
     * @param contentFilter filter to be used when writing the content names
     * @return the list of contents whose names has been added to the manifest and their content will be added to the
     * bundle.
     */
    private static List<Content> appendManifestContents(
            StringBuilder manifest,
            PrintWriter errors,
            List<? extends Component> components,
            ComponentVisitor componentVisitor,
            ContentFilter contentFilter) {

        manifest.append("Requested components:\n\n");
        ContentContainer contentsContainer = new ContentContainer(contentFilter, components);
        for (Component component : components) {
            try {
                if (components.stream().anyMatch(c -> c.supersedes(component))) {
                    continue;
                }

                manifest.append("  * ").append(component.getDisplayName()).append("\n\n");
                LOGGER.log(Level.FINE, "Start processing " + component.getDisplayName());
                long startTime = System.currentTimeMillis();
                componentVisitor.visit(contentsContainer, component);
                LOGGER.log(
                        Level.FINE,
                        "Took " + (System.currentTimeMillis() - startTime) + "ms" + " to process component "
                                + component.getDisplayName());
                Set<String> names = contentsContainer.getLatestNames();
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
                Functions.printStackTrace(e, errors);
                errors.println();
            }
        }
        return contentsContainer.getContents();
    }

    private static class ContentContainer extends Container {
        private final List<Content> contents = new ArrayList<>();
        private final Set<String> names = new HashSet<>();

        // The filter to return the names filtered
        private final ContentFilter contentFilter;

        private final List<? extends Component> components;

        /**
         * We need the filter to be able to filter the contents written to the manifest
         * @param contentFilter filter to use when writing the name of the contents
         */
        ContentContainer(ContentFilter contentFilter, List<? extends Component> components) {
            this.contentFilter = contentFilter;
            this.components = components;
        }

        @Override
        public void add(Content content) {
            if (content != null) {
                String name = getNameFiltered(contentFilter, content.getName(), content.getFilterableParameters());
                synchronized (this) {
                    contents.add(content);
                    names.add(name);
                }
            }
        }

        @Override
        public List<? extends Component> getComponents() {
            return components;
        }

        synchronized Set<String> getLatestNames() {
            Set<String> copy = new TreeSet<>(names);
            names.clear();
            return copy;
        }

        synchronized List<Content> getContents() {
            return new ArrayList<>(contents);
        }
    }

    public List<LogRecord> getAllLogRecords() {
        return handler.getRecent();
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void loadConfig() throws IOException {
        SupportPlugin instance = getInstance();
        if (instance != null) instance.load();
    }

    private static final boolean logStartupPerformanceIssues =
            Boolean.getBoolean(SupportPlugin.class.getCanonicalName() + ".threadDumpStartup");
    private static final int secondsPerThreadDump =
            Integer.getInteger(SupportPlugin.class.getCanonicalName() + ".secondsPerTD", 60);

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

    /**
     * @return the {@link com.cloudbees.jenkins.support.api.SupportContext}
     * @deprecated usage removed
     */
    @NonNull
    @Deprecated
    public synchronized SupportContextImpl getContext() {
        return context;
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
                final Future<List<LogRecord>> future = CallAsyncWrapper.callAsync(channel, new LogFetcher());
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
                            final LogRecord lr = new LogRecord(Level.WARNING, "Could not retrieve remote log records");
                            lr.setThrown(e1);
                            records = Collections.singletonList(lr);
                        } catch (ExecutionException e1) {
                            final LogRecord lr = new LogRecord(Level.WARNING, "Could not retrieve remote log records");
                            lr.setThrown(e1);
                            records = Collections.singletonList(lr);
                        } catch (TimeoutException e1) {
                            final LogRecord lr = new LogRecord(Level.WARNING, "Could not retrieve remote log records");
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
     * @deprecated use {@link BundleFileName#generate()} instead
     */
    @Deprecated
    @NonNull
    public static String getBundleFileName() {
        return BundleFileName.generate();
    }

    public static class LogHolder {
        private static final SupportLogHandler AGENT_LOG_HANDLER = new SupportLogHandler(256, 2048, 8);
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
            // avoid double installation of the handler. JNLP agents can reconnect to the controller multiple times
            // and each connection gets a different RemoteClassLoader, so we need to evict them by class name,
            // not by their identity.
            closeAll();
            Runtime.getRuntime().addShutdownHook(new Thread(LogInitializer::closeAll, "close log handlers"));
            LogHolder.AGENT_LOG_HANDLER.setLevel(level);
            LogHolder.AGENT_LOG_HANDLER.setDirectory(new File(rootPath.getRemote(), SUPPORT_DIRECTORY_NAME), "all");
            ROOT_LOGGER.addHandler(LogHolder.AGENT_LOG_HANDLER);
            return null;
        }

        private static void closeAll() {
            for (Handler h : ROOT_LOGGER.getHandlers()) {
                if (h.getClass()
                        .getName()
                        .equals(LogHolder.AGENT_LOG_HANDLER.getClass().getName())) {
                    ROOT_LOGGER.removeHandler(h);
                    try {
                        h.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }
    }

    public static class LogFetcher extends MasterToSlaveCallable<List<LogRecord>, RuntimeException> {
        private static final long serialVersionUID = 1L;

        public List<LogRecord> call() throws RuntimeException {
            return new ArrayList<>(LogHolder.AGENT_LOG_HANDLER.getRecent());
        }
    }

    public static class LogUpdater extends MasterToSlaveCallable<Void, RuntimeException> {

        private static final long serialVersionUID = 1L;

        private final Level level;

        public LogUpdater(Level level) {
            this.level = level;
        }

        public Void call() throws RuntimeException {
            LogHolder.AGENT_LOG_HANDLER.setLevel(level);
            return null;
        }
    }

    @Extension
    public static class ComputerListenerImpl extends ComputerListener {
        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            final Node node = c.getNode();
            if (node == null || node instanceof Jenkins) {
                return;
            }
            try {
                final VirtualChannel channel = c.getChannel();
                if (channel != null) {
                    final FilePath rootPath = node.getRootPath();
                    if (rootPath != null) {
                        CallAsyncWrapper.callAsync(channel, new LogInitializer(rootPath, getLogLevel()));
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(SupportPlugin.class.getName())
                        .log(Level.WARNING, "Could not install root log handler on node: " + c.getName(), e);
            } catch (RuntimeException e) {
                Logger.getLogger(SupportPlugin.class.getName())
                        .log(Level.WARNING, "Could not install root log handler on node: " + c.getName(), e);
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
        public long getInitialDelay() {
            return TimeUnit.MINUTES.toMillis(3);
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
            SupportAutomatedBundleConfiguration automatedBundleConfig = SupportAutomatedBundleConfiguration.get();
            if (nextBundleWrite.get() < System.currentTimeMillis() && automatedBundleConfig.isEnabled()) {
                if (thread != null && thread.isAlive()) {
                    LOGGER.log(Level.INFO, "Periodic bundle generating thread is still running. Execution aborted.");
                    return;
                }
                try {
                    thread = new Thread(
                            () -> {
                                nextBundleWrite.set(System.currentTimeMillis()
                                        + TimeUnit.HOURS.toMillis(automatedBundleConfig.getPeriod()));
                                thread.setName(String.format(
                                        "%s periodic bundle generator: since %s",
                                        SupportPlugin.class.getSimpleName(), new Date()));
                                try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                                    File bundleDir = getRootDirectory();
                                    if (!bundleDir.exists()) {
                                        if (!bundleDir.mkdirs()) {
                                            return;
                                        }
                                    }

                                    File file = new File(bundleDir, BundleFileName.generate());
                                    thread.setName(String.format(
                                            "%s periodic bundle generator: writing %s since %s",
                                            SupportPlugin.class.getSimpleName(), file.getName(), new Date()));
                                    try (FileOutputStream fos = new FileOutputStream(file)) {
                                        writeBundle(fos, automatedBundleConfig.getComponents());
                                    } finally {
                                        cleanupOldBundles(bundleDir, file);
                                    }
                                } catch (Throwable t) {
                                    LOGGER.log(Level.WARNING, "Could not save support bundle", t);
                                }
                            },
                            SupportPlugin.class.getSimpleName() + " periodic bundle generator");
                    thread.start();
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "Periodic bundle generating thread failed with error", t);
                }
            }
        }

        @SuppressFBWarnings(
                value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "IS2_INCONSISTENT_SYNC"},
                justification = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE=Best effort, "
                        + "IS2_INCONSISTENT_SYNC=only called from an already synchronized method")
        private void cleanupOldBundles(File bundleDir, File justGenerated) {
            thread.setName(String.format(
                    "%s periodic bundle generator: tidying old bundles since %s",
                    SupportPlugin.class.getSimpleName(), new Date()));
            File[] files = bundleDir.listFiles((dir, name) -> name.endsWith(".zip"));
            if (files == null) {
                LOGGER.log(
                        Level.WARNING,
                        "Something is wrong: {0} does not exist or there was an IO issue.",
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
                            LOGGER.log(Level.INFO, "Deleted old bundle {0}", f.getName());
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
        public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
            if (json.has("supportProvider")) {
                try {
                    getInstance()
                            .setSupportProvider(
                                    req.bindJSON(SupportProvider.class, json.getJSONObject("supportProvider")));
                } catch (IOException e) {
                    throw new FormException(e, "supportProvider");
                }
            }
            return true;
        }
    }
}
