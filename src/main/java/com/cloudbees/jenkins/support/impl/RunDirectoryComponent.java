package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Run;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
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
            String relativeToRoot = getBuildRelativePath(item, itemRootDir);
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

    private static String getBuildRelativePath(@NonNull Run item, @NonNull File itemRootDir) {

        // See Jenkins#isDefaultBuildDir that has restriction and cannot be used outside Jenkins core
        File buildsRootDir = "${ITEM_ROOTDIR}/builds".equals(Jenkins.get().getRawBuildsDir())
                ? new File(Jenkins.get().getRootDir(), "jobs")
                // Not using the default buildsDir, find the custom root dir by keeping what's before the compulsory
                // ${ITEM_FULL_NAME} placeholder
                : new File(Objects.requireNonNull(Util.replaceMacro(
                        Jenkins.get().getRawBuildsDir().replaceFirst("\\$\\{ITEM_FULL_NAME.*", ""),
                        Map.of("JENKINS_HOME", Jenkins.get().getRootDir().getPath()))));

        return buildsRootDir
                        .toPath()
                        .relativize(itemRootDir.toPath())
                        .toString()
                        .replace(File.separatorChar, '/');
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

        static final List<String> EXCLUDES = List.of(
                // https://github.com/jenkinsci/jenkins/blob/9f790a3142c76a33f1dbd8409715b867a53836c8/core/src/main/java/hudson/model/Run.java#L1143
                "archive/",
                // https://github.com/jenkinsci/workflow-api-plugin/blob/2e338a5c7a3c17be60e168faf80e7dbbdd47ceb5/src/main/java/org/jenkinsci/plugins/workflow/flow/StashManager.java#L254
                "stashes/",
                // https://github.com/jenkinsci/junit-attachments-plugin/blob/7e439b0efd070a5bf4ea50f51a3296e35ca5f814/src/main/java/hudson/plugins/junitattachments/AttachmentPublisher.java#L39
                "junit-attachments/",
                // https://github.com/jenkinsci/warnings-ng-plugin/blob/8c41827040ae8f8659a1c370b3efcb364c60cd29/plugin/src/main/java/io/jenkins/plugins/analysis/core/util/AffectedFilesResolver.java#L32
                "files-with-issues/",
                // https://github.com/jenkinsci/jacoco-plugin/blob/f5ea36e9aff4db394bb88944139bb7bb8f55187d/src/main/java/hudson/plugins/jacoco/JacocoReportDir.java#L20
                "jacoco/");

        public DescriptorImpl() {
            super("", EXCLUDES.stream().collect(Collectors.joining(",")), true, DEFAULT_MAX_DEPTH);
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
