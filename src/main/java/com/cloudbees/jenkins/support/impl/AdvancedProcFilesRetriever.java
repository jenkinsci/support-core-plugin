package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FilePathContent;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Base class for gathering specified /proc files with the capacity of selecting whether filter or not each file
 * content. You may want to filter some files and not doing so to other files.
 */
public abstract class AdvancedProcFilesRetriever extends ProcFilesRetriever {
    private static final Logger LOGGER = Logger.getLogger(AdvancedProcFilesRetriever.class.getName());

    /**
     * If you want to use this method, it's best to use {@link ProcFilesRetriever}.
     */
    @Override
    @Restricted(DoNotUse.class)
    public Map<String, String> getFilesToRetrieve() {
        LOGGER.warning("No proc files will be included to the bundle. You should use the class ProcFilesRetriever or the method AdvancedProdFilesRetriever#getProcFilesToRetrieve");
        return Collections.emptyMap();
    }

    /**
     * The method to define the files to include in the bundle. You add {@link ProcFile} to be able to specify file by
     * file whether its content should be filtered or not.
     * @return a set of files to be included in the bundle
     */
    public abstract Set<ProcFile> getProcFilesToRetrieve();

    @Override
    protected void addUnixContents(@NonNull Container container, final @NonNull Node node) {
        Computer c = node.toComputer();
        if (c == null || c.isOffline()) {
            return;
        }
        // fast path bailout for Windows
        if (!Boolean.TRUE.equals(c.isUnix())) {
            return;
        }
        SystemPlatform nodeSystemPlatform = getSystemPlatform(node);
        if (!SystemPlatform.LINUX.equals(nodeSystemPlatform)) {
            return;
        }
        String name;
        if (node instanceof Jenkins) {
            name = "master";
        } else {
            name = "slave/" + node.getNodeName();
        }

        for (ProcFile procDescriptor : getProcFilesToRetrieve()) {
            container.add(new FilePathContent("nodes/{0}/proc/{1}", new String[]{name, procDescriptor.getName()},
                    new FilePath(c.getChannel(), procDescriptor.getFile())) {

                        @Override
                        public boolean shouldBeFiltered() {
                            // Whether this specific file should be filtered or not
                            return procDescriptor.isFiltered();
                        }
            });
        }

        afterAddUnixContents(container, node, name);
    }

    /**
     * A class to define a file with a name in the bundle and also whether its content should be filtered.
     */
    public static class ProcFile {
        private String file;
        private String name;
        private boolean filtered;

        private ProcFile(@Nonnull String file, @Nonnull String name, boolean filtered) {
            this.file = file;
            this.name = name;
            this.filtered = filtered;
        }

        public static ProcFile of(@Nonnull String file, @Nonnull String name, boolean filtered) {
            return new ProcFile(file, name, filtered);
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isFiltered() {
            return filtered;
        }

        public void setFiltered(boolean filtered) {
            this.filtered = filtered;
        }
    }
}
