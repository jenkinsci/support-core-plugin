package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FilePathContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;

/**
 * @author Allan Burdajewicz
 */
@Extension
public class NodeRemoteDirectoryComponent extends DirectoryComponent<Computer> implements Serializable {

    public NodeRemoteDirectoryComponent() {
        super();
    }

    @DataBoundConstructor
    public NodeRemoteDirectoryComponent(String includes, String excludes, boolean defaultExcludes, int maxDepth) {
        super(includes, excludes, defaultExcludes, maxDepth);
    }

    @Override
    public void addContents(@NonNull Container container, Computer item) {
        if (item.getNode() == null || item.isOffline()) {
            return;
        }
        try {
            Arrays.stream(Objects.requireNonNull(item.getNode().getRootPath())
                    .list(getIncludes(), getExcludes(), getDefaultExcludes()))
                    .forEach(filePath -> {

                        Path relativePath = Paths.get(item.getNode().getRootPath().getRemote())
                                .relativize(Paths.get(filePath.getRemote()));
                        if (relativePath.getNameCount() <= getMaxDepth()) {
                            container.add(new FilePathContent(
                                    "nodes/slave/{0}/remote/{1}",
                                    new String[]{item.getNode().getNodeName(), relativePath.toString()},
                                    filePath)
                            );
                        }
                    });
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Could not list files from remote directory of " + item.getNode().getNodeName(), e);
        }

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
            setIncludes("");
            setExcludes("workspace/**, remoting/jarCache/**");
            setDefaultExcludes(true);
            setMaxDepth(DEFAULT_MAX_DEPTH);
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
            if (computer != null && computer.getNode() != null) {
                return FilePath.validateFileMask(computer.getNode().getRootPath(), includes, true);
            }
            return FormValidation.ok();
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
            if (computer != null && computer.getNode() != null) {
                return FilePath.validateFileMask(computer.getNode().getRootPath(), excludes, true);
            }
            return FormValidation.ok();
        }
    }

}
