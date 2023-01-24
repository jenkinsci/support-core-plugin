package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.cloudbees.jenkins.support.SupportPlugin;


/**
 * <p>
 * Iterate over the cache entries stored by the {@link SmartLogFetcher} and remove those which 
 * belong to agents that are no longer attached.
 * </p>
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
    SmartLogCleaner(final String id, final Set<String> cackeKeys) {
        this.rootCacheDir = new File(SupportPlugin.getLogsDirectory(), id);
        this.cacheKeys = cackeKeys;
    }

    void execute() {
        File[] cacheKeyDirs = rootCacheDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
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
                            if (cacheKeys.contains(dir.getName())) {
                                LOGGER.log(Level.FINE, "cacheKey belongs to agent, keeping the directory '{0}'", dir.getName());
                            } else {
                                try {
                                    FileUtils.deleteDirectory(dir);
                                    LOGGER.log(Level.INFO, "The agent is no longer available, cache entry {0} was deleted", dir.getAbsolutePath() );
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

    private static final Logger LOGGER = Logger.getLogger(SmartLogCleaner.class.getName());
}
