package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FilePathContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Allan Burdajewicz
 */
@Extension
public class NodeRemoteDirectoryComponent extends DirectoryComponent<Computer> implements Serializable {

    @Override
    public int getHash() {
        return 26;
    }

    public NodeRemoteDirectoryComponent() {
        super();
    }

    @DataBoundConstructor
    public NodeRemoteDirectoryComponent(String includes, String excludes, boolean defaultExcludes, int maxDepth) {
        super(includes, excludes, defaultExcludes, maxDepth);
    }

    @Override
    public void addContents(@NonNull Container container, @NonNull Computer item) {
        Node node = item.getNode();
        if (node == null || item.isOffline()) {
            return;
        }

        FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            LOGGER.log(Level.WARNING, "Node " + node.getDisplayName() + " seems to be offline");
            return;
        }

        try {
            Arrays.stream(rootPath.list(getIncludes(), getExcludes(), getDefaultExcludes()))
                    .forEach(filePath -> {
                        Path relativePath = Paths.get(rootPath.getRemote()).relativize(Paths.get(filePath.getRemote()));
                        if (relativePath.getNameCount() <= getMaxDepth()) {
                            container.add(new FilePathContent(
                                    "nodes/slave/{0}/remote/{1}",
                                    new String[] {
                                        node.getNodeName(),
                                        Functions.isWindows()
                                                ? relativePath.toString().replace('\\', '/')
                                                : relativePath.toString()
                                    },
                                    filePath));
                        }
                    });
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Could not list files from remote directory of " + node.getNodeName(), e);
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.AGENT;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return Messages.NodeRemoteDirectoryComponent_DisplayName();
    }

    @Override
    public boolean isApplicable(Computer item) {
        return item != Jenkins.get().toComputer();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("nodeRemoteDirectoryComponent")
    public static class DescriptorImpl extends DirectoryComponentsDescriptor<Computer> {

        static final int DEFAULT_MAX_DEPTH = 10;

        public DescriptorImpl() {
            super("remoting/**, support/**", "remoting/jarCache/**", true, DEFAULT_MAX_DEPTH);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.NodeRemoteDirectoryComponent_DisplayName();
        }

        /**
         * Form validation for the ant style patterns to include.
         *
         * @param includes the ant style patterns
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        @SuppressWarnings("unused") // used by Stapler
        public FormValidation doCheckIncludes(@AncestorInPath Computer computer, @QueryParameter String includes)
                throws IOException {
            Node node = computer.getNode();
            if (node == null || computer.isOffline()) {
                return FormValidation.ok();
            }

            FilePath rootPath = node.getRootPath();
            if (rootPath == null) {
                return FormValidation.ok();
            }

            return FilePath.validateFileMask(rootPath, includes, true);
        }

        /**
         * Form validation for the ant style patterns to exclude.
         *
         * @param excludes the ant style patterns
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        @SuppressWarnings("unused") // used by Stapler
        public FormValidation doCheckExcludes(@AncestorInPath Computer computer, @QueryParameter String excludes)
                throws IOException {
            Node node = computer.getNode();
            if (node == null || computer.isOffline()) {
                return FormValidation.ok();
            }

            FilePath rootPath = node.getRootPath();
            if (rootPath == null) {
                return FormValidation.ok();
            }

            return FilePath.validateFileMask(rootPath, excludes, true);
        }
    }
}
