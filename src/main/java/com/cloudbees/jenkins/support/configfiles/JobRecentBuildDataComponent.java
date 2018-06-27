/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.ItemComponent;
import com.cloudbees.jenkins.support.api.ItemComponentDescriptor;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.security.Permission;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link ItemComponent} that adds the build folder for a specified number of
 * recent builds of the associated item to the support bundle.
 */
@Restricted(NoExternalUse.class)
public class JobRecentBuildDataComponent extends ItemComponent {

    private int recentBuildsToInclude = 1;

    @DataBoundConstructor
    public JobRecentBuildDataComponent() {
    }

    public int getRecentBuildsToInclude() {
        return recentBuildsToInclude;
    }

    @DataBoundSetter
    public void setRecentBuildsToInclude(int recentBuildsToInclude) {
        this.recentBuildsToInclude = Math.max(recentBuildsToInclude, 1);
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(Container container) {
        Job job = getItem(Job.class);
        Run run = job.getLastBuild();
        for (int i = 0; run != null && i < recentBuildsToInclude; i++, run = run.getPreviousBuild()) {
            final Path outputDir = getItemRootDestination().resolve("builds");
            try {
                new DirScanner.Full().scan(run.getRootDir(), new FileVisitor() {
                    @Override
                    public void visit(File file, String relativePath) throws IOException {
                        if (file.isFile()) {
                            Path bundlePath = outputDir.resolve(relativePath);
                            if (file.getName().endsWith(".xml")) {
                                container.add(new XmlRedactedSecretFileContent(bundlePath.toString(), file));
                            } else {
                                container.add(new FileContent(bundlePath.toString(), file));
                            }
                        }
                    }
                    @Override
                    public boolean understandsSymlink() {
                       return true;
                    }
                    @Override
                    public void visitSymlink(File link, String target, String relativePath) throws IOException {
                        // Ignore symlinks
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends ItemComponentDescriptor {
        @Override
        public String getDisplayName() {
            return "Recent build data files and logs (Encrypted secrets are redacted from XML files)";
        }

        @Override
        public boolean isApplicable(TopLevelItem item) {
            return item instanceof Job;
        }

        public FormValidation doCheckRecentBuildsToInclude(@QueryParameter int recentBuildsToInclude) {
            if (recentBuildsToInclude <= 0) {
                return FormValidation.error("Must be a positive number");
            }
            return FormValidation.ok();
        }
    }

}
