package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.cloudbees.jenkins.support.SupportPlugin;

import hudson.Util;
import hudson.model.Node;

/**
 * <p>
 * Iterate over the cache entries stored by the {@link SmartLogFetcher} and remove those which 
 * belong to agents that are no longer attached.
 */
class SmartLogCleaner {

    private final File rootCacheDir;
    private final Set<String> cacheKeys;

    /**
     * @param id
     *      SmartLogCleaner only supports one directory full of log files.
     *      So different IDs would have to be specified for different log files from different directories.
     * @param nodes
     *      Used to generate the cache keys to match within the target directory.
     */
    SmartLogCleaner(final String id, final List<Node> nodes) {
        this.rootCacheDir = new File(SupportPlugin.getRootDirectory(), id);
        this.cacheKeys = getActiveCacheKeys(nodes);
    }

    void execute() {
        File[] cacheKeyDirs = rootCacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
              return new File(current, name).isDirectory();
            }
        });

        if (cacheKeyDirs == null || cacheKeyDirs.length == 0) {
            LOGGER.log(Level.FINE, "cacheKeys directory '{0]' is empty, nothing to clean up", rootCacheDir);
        } else {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        for (File dir: cacheKeyDirs) {
                            if (dir.isDirectory() && dir.exists() && cacheKeys.contains(dir.getName())) {
                                LOGGER.log(Level.FINE, "cacheKey belongs to agent, keeping the directory '{0}'", dir.getName());
                            } else {
                                try {
                                    FileUtils.deleteDirectory(dir);
                                    LOGGER.log(Level.INFO, "The agent is no longer available, cache entry {0} was deleted", dir.getName());
                                } catch (IOException e) {
                                    LOGGER.log(Level.WARNING, "Couldn't remove the cache directory " + dir.getName(), e);
                                }
                            }
                        }
                    }
                });
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "The clean up task has ended with errors", e);
            } finally {
                executor.shutdown();
            }
        }
    }

    private Set<String> getActiveCacheKeys(final List<Node> nodes) {
        Set<String> cacheKeys = new HashSet<>(nodes.size());
        for (Node node : nodes) {
            // can't use node.getRootPath() cause won't work with disconnected agents.
            String cacheKey = Util.getDigestOf(node.getNodeName() + ":" + ((hudson.model.Slave)node).getRemoteFS());
            LOGGER.log(Level.FINEST, "cacheKey {0} is active", cacheKey);
            cacheKeys.add(StringUtils.right(cacheKey, 8));
        }
        return cacheKeys;
    }

    private static final Logger LOGGER = Logger.getLogger(SmartLogCleaner.class.getName());
}