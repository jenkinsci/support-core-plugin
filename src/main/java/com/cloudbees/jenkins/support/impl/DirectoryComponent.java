package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.security.Permission;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;

public abstract class DirectoryComponent<T extends AbstractModelObject> extends ObjectComponent<T>
        implements ExtensionPoint {

    static final Logger LOGGER = Logger.getLogger(DirectoryComponent.class.getName());

    private String includes;
    private String excludes;
    private int maxDepth;
    private boolean defaultExcludes;

    public DirectoryComponent() {
        super();
        setExcludes(getDescriptor().getExcludes());
        setIncludes(getDescriptor().getIncludes());
        setDefaultExcludes(getDescriptor().isDefaultExcludes());
        setMaxDepth(getDescriptor().getMaxDepth());
    }

    public DirectoryComponent(String includes, String excludes, boolean defaultExcludes, int maxDepth) {
        super();
        setExcludes(excludes);
        setIncludes(includes);
        setDefaultExcludes(defaultExcludes);
        setMaxDepth(maxDepth);
    }

    protected final void list(File dir, FileVisitor visitor) throws IOException {
        DirScanner scan = new DirGlobScanner(getIncludes(), getExcludes(), getDefaultExcludes(), false);
        scan.scan(dir, new FileVisitor() {

            @Override
            public void visit(File file, String s) throws IOException {
                if (Paths.get(s).getNameCount() <= getMaxDepth()) {
                    visitor.visit(file, s);
                }
            }

            @Override
            public void visitSymlink(File link, String target, String relativePath) throws IOException {
                if (Paths.get(relativePath).getNameCount() <= getMaxDepth()) {
                    visitor.visitSymlink(link, target, relativePath);
                }
            }

            @Override
            public boolean understandsSymlink() {
                return true;
            }
        });
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public boolean getDefaultExcludes() {
        return defaultExcludes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public void setDefaultExcludes(boolean defaultExcludes) {
        this.defaultExcludes = defaultExcludes;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public DirectoryComponentsDescriptor<T> getDescriptor() {
        return Jenkins.get().getDescriptorByType(DirectoryComponentsDescriptor.class);
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Files in Directory";
    }

    public static class DirectoryComponentsDescriptor<T extends AbstractModelObject>
            extends ObjectComponentDescriptor<T> {

        static final int DEFAULT_MAX_DEPTH = 10;

        private String includes;
        private String excludes;
        private boolean defaultExcludes;
        private int maxDepth;

        public DirectoryComponentsDescriptor() {
            this("", "", true, DEFAULT_MAX_DEPTH);
        }

        public DirectoryComponentsDescriptor(String includes, String excludes, boolean defaultExcludes, int maxDepth) {
            super();
            setIncludes(includes);
            setExcludes(excludes);
            setMaxDepth(maxDepth);
            setDefaultExcludes(defaultExcludes);
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Files in Directory";
        }

        public String getIncludes() {
            return includes;
        }

        public void setIncludes(String includes) {
            this.includes = includes;
        }

        public String getExcludes() {
            return excludes;
        }

        public void setExcludes(String excludes) {
            this.excludes = excludes;
        }

        public boolean isDefaultExcludes() {
            return defaultExcludes;
        }

        public void setDefaultExcludes(boolean defaultExcludes) {
            this.defaultExcludes = defaultExcludes;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }

        @Restricted(NoExternalUse.class) // stapler
        @SuppressWarnings("unused") // used by Stapler
        public FormValidation doCheckMaxDepth(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }
    }

    public static class DirGlobScanner extends DirScanner {

        private final String includes;
        private final String excludes;
        private boolean useDefaultExcludes;
        private boolean followSymlinks;
        private static final long serialVersionUID = 1L;

        public DirGlobScanner(String includes, String excludes, boolean useDefaultExcludes, boolean followSymlinks) {
            this.includes = includes;
            this.excludes = excludes;
            this.useDefaultExcludes = useDefaultExcludes;
            this.followSymlinks = followSymlinks;
        }

        public void scan(File dir, FileVisitor visitor) throws IOException {
            FileSet fileSet = Util.createFileSet(
                    dir,
                    Optional.ofNullable(Util.fixEmpty(this.includes)).orElse("**/*"),
                    Optional.ofNullable(Util.fixEmpty(this.excludes)).orElse(""));
            fileSet.setDefaultexcludes(this.useDefaultExcludes);
            fileSet.setFollowSymlinks(followSymlinks);
            if (dir.exists()) {
                DirectoryScanner dirScanner = fileSet.getDirectoryScanner(new Project());
                String[] var5 = (String[]) ArrayUtils.addAll(
                        dirScanner.getIncludedFiles(),
                        Stream.of(dirScanner.getNotFollowedSymlinks())
                                .map(s -> dir.toPath().relativize(Paths.get(s)).toString())
                                .toArray());
                int var6 = var5.length;

                for (int var7 = 0; var7 < var6; ++var7) {
                    String f = var5[var7];
                    File file = new File(dir, f);
                    this.scanSingle(file, f, visitor);
                }
            }
        }
    }
}
