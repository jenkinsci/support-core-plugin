/*
 * Copyright © 2013 CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.model.About;
import com.cloudbees.jenkins.support.model.Items;
import com.cloudbees.jenkins.support.model.Nodes;
import com.cloudbees.jenkins.support.util.Helper;
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
import hudson.remoting.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.Stapler;

import javax.annotation.CheckForNull;
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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
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
        About a = new AboutContent().generate();
        container.add(new YamlContent("about.yaml", a));
        container.add(new MarkdownContent("about.md", a));


        Items i = new ItemsContent().generate();
        container.add(new YamlContent("items.yaml", i));
        container.add(new MarkdownContent("items.md", i));


        Nodes n = new NodesContent().generate();
        container.add(new YamlContent("nodes.yaml", n));
        container.add(new MarkdownContent("nodes.md", n));

        container.add(new ActivePlugins("plugins/active.txt"));
        container.add(new DisabledPlugins());
        container.add(new FailedPlugins());

        // container.add(new ActivePlugins("docker/plugins.txt"));
        container.add(new Dockerfile());

        container.add(new MasterChecksumsContent());
        for (final Node node : Helper.getActiveInstance().getNodes()) {
            container.add(new NodeChecksumsContent(node));
        }
    }

    private static String getDescriptorName(@CheckForNull Describable<?> d) {
        if (d == null) {
            return "(none)";
        }
        return "`" + d.getClass().getName() + "`";
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
            for (File file : FileUtils.listFiles(rootPath, null, false)) {
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

        /** {@inheritDoc} */
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO: do we have to verify some role?
        }
    }

    private static class ActivePlugins extends PrintedContent {
        public ActivePlugins(String path) {
            super(path);
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            PluginManager pluginManager = Helper.getActiveInstance().getPluginManager();
            List<PluginWrapper> plugins = pluginManager.getPlugins();
            Collections.sort(plugins);
            for (PluginWrapper w : plugins) {
                if (w.isActive()) {
                    out.println(w.getShortName() + ":" + w.getVersion() + ":" + (w.isPinned() ? "pinned" : "not-pinned"));
                }
            }
        }
    }

    private static class DisabledPlugins extends PrintedContent {
        public DisabledPlugins() {
            super("plugins/disabled.txt");
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            PluginManager pluginManager = Helper.getActiveInstance().getPluginManager();
            List<PluginWrapper> plugins = pluginManager.getPlugins();
            Collections.sort(plugins);
            for (PluginWrapper w : plugins) {
                if (!w.isActive()) {
                    out.println(w.getShortName() + ":" + w.getVersion() + ":" + (w.isPinned() ? "pinned" : "not-pinned"));
                }
            }
        }
    }

    private static class FailedPlugins extends PrintedContent {
        public FailedPlugins() {
            super("plugins/failed.txt");
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            PluginManager pluginManager = Helper.getActiveInstance().getPluginManager();
            List<PluginManager.FailedPlugin> plugins = pluginManager.getFailedPlugins();
            // no need to sort
            for (PluginManager.FailedPlugin w : plugins) {
                out.println(w.name + " -> " + w.cause);
            }
        }
    }

    private static class Dockerfile extends PrintedContent {
        public Dockerfile() {
            super("docker/Dockerfile");
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {

            PluginManager pluginManager = Helper.getActiveInstance().getPluginManager();
            String fullVersion = Jenkins.getVersion().toString();
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

            List<PluginWrapper> plugins = pluginManager.getPlugins();
            Collections.sort(plugins);

            List<PluginWrapper> activated = new ArrayList<PluginWrapper>();
            List<PluginWrapper> disabled = new ArrayList<PluginWrapper>();
            for (PluginWrapper w : plugins) {
                if (!w.isEnabled()) {
                    disabled.add(w);
                }
                if (w.isActive()) {
                    activated.add(w);
                }
            }

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
    }

    private static class MasterChecksumsContent extends PrintedContent {
        MasterChecksumsContent() {
            super("nodes/master/checksums.md5");
        }
        @Override protected void printTo(PrintWriter out) throws IOException {
            final Jenkins jenkins = Helper.getActiveInstance();
            if (jenkins == null) {
                // Lifecycle.get() depends on Jenkins instance, hence this method won't work in any case
                throw new IOException("Jenkins has not been started, or was already shut down");
            }

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
                        "/WEB-INF/slave.jar", // note that as of 2.33 this will not be present (anyway it is the same as war/WEB-INF/lib/remoting-*.jar, printed above)
                        "/WEB-INF/remoting.jar", // ditto
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

            final Collection<File> pluginFiles = FileUtils.listFiles(new File(jenkins.getRootDir(), "plugins"), null, false);
            for (File file : pluginFiles) {
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
