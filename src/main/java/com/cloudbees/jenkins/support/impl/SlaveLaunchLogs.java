/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

package com.cloudbees.jenkins.support.impl;

import static com.cloudbees.jenkins.support.impl.JenkinsLogs.ROTATED_LOGFILE_FILTER;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.LaunchLogsFileContent;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.init.Terminator;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import hudson.util.io.RewindableRotatingFileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.output.TeeOutputStream;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Adds agent launch logs, which captures the current and past running connections to the agent.
 */
@Extension
public class SlaveLaunchLogs extends ObjectComponent<Computer> {

    private static final int MAX_ROTATE_LOGS =
            Integer.getInteger(SlaveLaunchLogs.class.getName() + ".MAX_ROTATE_LOGS", 9);

    private static final Logger LOGGER = Logger.getLogger(SlaveLaunchLogs.class.getName());

    @DataBoundConstructor
    public SlaveLaunchLogs() {
        super();
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Agent Launch Logs";
    }

    @Override
    public int getHash() {
        return 37;
    }

    @Override
    public void addContents(@NonNull Container container) {
        ExtensionList.lookupSingleton(LogArchiver.class).addContents(container);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }

    @Override
    public void addContents(@NonNull Container container, Computer item) {
        if (item.getNode() != null
                && item.getLogFile().lastModified() >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)) {
            File dir = new File(Jenkins.get().getRootDir(), "logs/slaves/" + item.getName());
            File[] files = dir.listFiles(ROTATED_LOGFILE_FILTER);
            if (files != null) {
                for (File f : files) {
                    container.add(new LaunchLogsFileContent(
                            "nodes/slave/{0}/launchLogs/{1}",
                            new String[] {dir.getName(), f.getName()}, f, FileListCapComponent.MAX_FILE_SIZE));
                }
            }
        }
    }

    @Extension
    public static final class LogArchiver extends ConsoleLogFilter {

        private final File logDir;
        private final RewindableRotatingFileOutputStream stream;

        public LogArchiver() throws IOException {
            logDir = new File(SupportPlugin.getLogsDirectory(), "agent-launches");
            stream = new RewindableRotatingFileOutputStream(new File(logDir, "all.log"), MAX_ROTATE_LOGS);
            stream.rewind();
        }

        @Override
        public OutputStream decorateLogger(Computer computer, OutputStream logger) {
            if (computer instanceof SlaveComputer) {
                return new TeeOutputStream(logger, new PrefixedStream(stream, computer.getName()));
            } else {
                return logger;
            }
        }

        @SuppressWarnings("rawtypes")
        @Override
        public OutputStream decorateLogger(Run build, OutputStream logger) throws IOException, InterruptedException {
            return logger;
        }

        public void addContents(@NonNull Container container) {
            File[] files = logDir.listFiles(ROTATED_LOGFILE_FILTER);
            if (files != null) {
                for (File f : files) {
                    container.add(new LaunchLogsFileContent(
                            "nodes/slave/launches/" + f.getName(),
                            new String[0],
                            f,
                            FileListCapComponent.MAX_FILE_SIZE));
                }
            }
        }

        @Terminator
        public static void close() {
            try {
                ExtensionList.lookupSingleton(LogArchiver.class).stream.close();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
        }
    }

    static class PrefixedStream extends LineTransformationOutputStream.Delegating {
        private final String name;

        PrefixedStream(OutputStream out, String name) {
            super(out);
            this.name = name;
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            synchronized (out) {
                out.write('[');
                out.write(DateTimeFormatter.ISO_INSTANT
                        .format(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                        .getBytes(StandardCharsets.US_ASCII));
                out.write(' ');
                out.write(name.getBytes(StandardCharsets.UTF_8));
                out.write(']');
                out.write(' ');
                out.write(b, 0, len);
            }
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public boolean isSelectedByDefault(Computer item) {
        return true;
    }

    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz) || Computer.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean isApplicable(Computer item) {
        return item != Jenkins.get().toComputer();
    }

    @Override
    public SlaveLaunchLogs.DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(SlaveLaunchLogs.DescriptorImpl.class);
    }

    @Extension
    @Symbol("agentsLaunchLogsComponent")
    public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Agent Launch Logs";
        }
    }
}
