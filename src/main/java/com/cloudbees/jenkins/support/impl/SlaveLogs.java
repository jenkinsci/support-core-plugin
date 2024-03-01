/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import static com.cloudbees.jenkins.support.SupportPlugin.SUPPORT_DIRECTORY_NAME;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

/**
 * Adds the agent logs from all of the machines
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete.
public class SlaveLogs extends Component {
    private static final Logger LOGGER = Logger.getLogger(SlaveLogs.class.getCanonicalName());

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Agent Log Recorders";
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public void addContents(@NonNull Container container) {
        // expensive remote computation are pooled together and executed later concurrently across all the agents
        List<java.util.concurrent.Callable<List<FileContent>>> tasks = Lists.newArrayList();
        SmartLogFetcher logFetcher = new SmartLogFetcher(
                "cache", new LogFilenameAgentFilter()); // id is awkward because of backward compatibility
        SmartLogFetcher winswLogFetcher = new SmartLogFetcher("winsw", new WinswLogfileFilter());

        List<Node> nodes = Jenkins.get().getNodes();
        for (final Node node : nodes) {
            if (node.toComputer() instanceof SlaveComputer) {
                container.add(new LogRecordContent("nodes/slave/{0}/jenkins.log", node.getNodeName()) {
                    @Override
                    public Iterable<LogRecord> getLogRecords() throws IOException {
                        Computer computer = node.toComputer();
                        if (computer == null) {
                            return Collections.emptyList();
                        } else {
                            try {
                                return Lists.reverse(new ArrayList<>(computer.getLogRecords()));
                            } catch (InterruptedException e) {
                                throw new IOException(e);
                            }
                        }
                    }
                });
            }

            addAgentJulLogRecords(container, tasks, node, logFetcher);
            addWinsStdoutStderrLog(tasks, node, winswLogFetcher);
        }

        Set<String> activeCacheKeys = getActiveCacheKeys(nodes);
        new SmartLogCleaner("winsw", activeCacheKeys).execute();
        new SmartLogCleaner("cache", activeCacheKeys).execute();

        // execute all the expensive computations in parallel to speed up the time
        if (!tasks.isEmpty()) {
            try {
                long expiresNanoTime =
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC);
                for (java.util.concurrent.Future<List<FileContent>> r : Computer.threadPoolForRemoting.invokeAll(
                        tasks, SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                    try {
                        for (FileContent c :
                                r.get(Math.max(1, expiresNanoTime - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                            container.add(c);
                        }
                    } catch (ExecutionException e) {
                        LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
                    } catch (TimeoutException e) {
                        LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
                        r.cancel(false);
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
            }
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }

    /**
     * Captures a "recent" (but still fairly large number of) j.u.l entries written on this agent.
     *
     * @see JenkinsLogs#addControllerJulLogRecords(Container)
     */
    private void addAgentJulLogRecords(
            Container result,
            List<java.util.concurrent.Callable<List<FileContent>>> tasks,
            final Node node,
            final SmartLogFetcher logFetcher) {
        final FilePath rootPath = node.getRootPath();
        if (rootPath != null) {
            // rotated log files stored on the disk
            tasks.add(new java.util.concurrent.Callable<List<FileContent>>() {
                public List<FileContent> call() throws Exception {
                    List<FileContent> result = new ArrayList<FileContent>();
                    FilePath supportPath = rootPath.child(SUPPORT_DIRECTORY_NAME);
                    if (supportPath.isDirectory()) {
                        final Map<String, File> logFiles =
                                logFetcher.forNode(node).getLogFiles(supportPath);
                        for (Map.Entry<String, File> entry : logFiles.entrySet()) {
                            result.add(new FileContent(
                                    "nodes/slave/{0}/logs/{1}",
                                    new String[] {node.getNodeName(), entry.getKey()}, entry.getValue()));
                        }
                    }
                    return result;
                }
            });
        }

        // this file captures the most recent of those that are still kept around in memory.
        // this overlaps with Jenkins.logRecords, and also overlaps with what's written in files,
        // but added nonetheless just in case.
        //
        // should be ignorable.
        result.add(new LogRecordContent("nodes/slave/{0}/logs/all_memory_buffer.log", node.getNodeName()) {
            @Override
            public Iterable<LogRecord> getLogRecords() throws IOException {
                try {
                    return SupportPlugin.getInstance().getAllLogRecords(node);
                } catch (InterruptedException e) {
                    throw (IOException) new InterruptedIOException().initCause(e);
                }
            }
        });
    }

    /**
     * Captures stdout/stderr log files produced by winsw.
     */
    private void addWinsStdoutStderrLog(
            List<java.util.concurrent.Callable<List<FileContent>>> tasks,
            final Node node,
            final SmartLogFetcher logFetcher) {
        final FilePath rootPath = node.getRootPath();
        if (rootPath != null) {
            // rotated log files stored on the disk
            tasks.add(new java.util.concurrent.Callable<List<FileContent>>() {
                public List<FileContent> call() throws Exception {
                    List<FileContent> result = new ArrayList<FileContent>();
                    // TODO presumes that WinSW’s %BASE% would be the remoteFS as in
                    // https://docs.cloudbees.com/docs/cloudbees-ci-kb/latest/client-and-managed-controllers/how-to-install-windows-agents-as-a-service
                    // and that
                    // https://github.com/winsw/winsw/blob/6cf303c1d3fbe1069d95af230b8efa117d29cdf2/src/WinSW.Core/Configuration/XmlServiceConfig.cs#L273
                    // is not overridden from e.g.
                    // https://github.com/winsw/winsw/blob/e4cf507bae5981363a9cdc0f7301c1aa892af401/samples/shared-directory-mapper.xml#L8
                    final Map<String, File> logFiles = logFetcher.forNode(node).getLogFiles(rootPath);
                    for (Map.Entry<String, File> entry : logFiles.entrySet()) {
                        result.add(new FileContent(
                                "nodes/slave/{0}/logs/winsw/{1}",
                                new String[] {node.getNodeName(), entry.getKey()},
                                entry.getValue(),
                                FileListCapComponent.MAX_FILE_SIZE));
                    }
                    return result;
                }
            });
        }
    }

    /**
     * Build a Set including the cacheKeys associated to every agent in the instance
     */
    private Set<String> getActiveCacheKeys(final List<Node> nodes) {
        Set<String> cacheKeys = new HashSet<>(nodes.size());
        for (Node node : nodes) {
            // can't use node.getRootPath() cause won't work with disconnected agents.
            String cacheKey = Util.getDigestOf(node.getNodeName() + ":"
                    + ((hudson.model.Slave) node).getRemoteFS()); // FIPS OK: Not security related.
            LOGGER.log(Level.FINEST, "cacheKey {0} is active", cacheKey);
            cacheKeys.add(StringUtils.right(cacheKey, 8));
        }
        return cacheKeys;
    }
}
