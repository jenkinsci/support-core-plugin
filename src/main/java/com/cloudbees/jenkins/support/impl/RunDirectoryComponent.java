package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Run;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
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
public class RunDirectoryComponent extends DirectoryComponent<Run> {

    public RunDirectoryComponent() {
        super();
    }

    @DataBoundConstructor
    public RunDirectoryComponent(String includes, String excludes, boolean defaultExcludes, int maxDepth) {
        super(includes, excludes, defaultExcludes, maxDepth);
    }

    @Override
    public void addContents(@NonNull Container container, @NonNull Run item) {
        try {
            File itemRootDir = item.getRootDir();
            String relativeToRoot = Functions.isWindows()
                    ? new File(Jenkins.get().getRootDir(), "jobs")
                            .toPath()
                            .relativize(itemRootDir.toPath())
                            .toString()
                            .replace('\\', '/')
                    : new File(Jenkins.get().getRootDir(), "jobs")
                            .toPath()
                            .relativize(itemRootDir.toPath())
                            .toString();
            list(itemRootDir, new FileVisitor() {

                @Override
                public void visitSymlink(File link, String target, String relativePath) {
                    container.add(
                            new PrintedContent(
                                    "items/{0}/{1}",
                                    relativeToRoot,
                                    Functions.isWindows() ? relativePath.replace('\\', '/') : relativePath) {

                                @Override
                                protected void printTo(PrintWriter out) {
                                    out.println("symlink -> " + target);
                                }

                                @Override
                                public boolean shouldBeFiltered() {
                                    return true;
                                }
                            });
                }

                @Override
                public void visit(File file, String s) {
                    container.add(new FileContent(
                            "items/{0}/{1}",
                            new String[] {relativeToRoot, Functions.isWindows() ? s.replace('\\', '/') : s}, file));
                }

                @Override
                public boolean understandsSymlink() {
                    return true;
                }
            });
        } catch (IOException e) {
            LOGGER.log(
                    Level.WARNING,
                    "Could not list files from root directory of "
                            + item.getParent().getFullName() + "#" + item.getNumber(),
                    e);
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.BUILDS;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return Messages.RunDirectoryComponent_DisplayName();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("runDirectoryComponent")
    public static class DescriptorImpl extends DirectoryComponentsDescriptor<Run> {

        static final int DEFAULT_MAX_DEPTH = 10;

        public DescriptorImpl() {
            super("", "**/artifacts/**, **/stashes/**", true, DEFAULT_MAX_DEPTH);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.RunDirectoryComponent_DisplayName();
        }

        /**
         * Form validation for the ant style patterns to include.
         *
         * @param includes the ant style patterns
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        @SuppressWarnings("unused") // used by Stapler
        public FormValidation doCheckIncludes(@AncestorInPath Run item, @QueryParameter String includes)
                throws IOException {
            if (item == null) {
                return FormValidation.ok();
            }
            try {
                FileSet fs = new FileSet();
                fs.setDir(item.getRootDir());
                fs.setProject(new Project());
                fs.setIncludes(includes);
                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error(e, "Could not parse the patterns");
            }
        }

        /**
         * Form validation for the ant style patterns to exclude.
         *
         * @param excludes the ant style patterns
         * @return the validation results.
         */
        @Restricted(NoExternalUse.class) // stapler
        @SuppressWarnings("unused") // used by Stapler
        public FormValidation doCheckExcludes(@AncestorInPath Run item, @QueryParameter String excludes) {
            if (item == null) {
                return FormValidation.ok();
            }
            try {
                FileSet fs = new FileSet();
                fs.setDir(item.getRootDir());
                fs.setProject(new Project());
                fs.setExcludes(excludes);
                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error(e, "Could not parse the patterns");
            }
        }
    }
}
