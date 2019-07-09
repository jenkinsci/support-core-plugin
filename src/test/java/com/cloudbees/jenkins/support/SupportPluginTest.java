package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

public class SupportPluginTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    @Issue("JENKINS-58393")
    public void testGenerateBundleExceptionHandler() throws Exception {
        List<Component> componentsToCreate = Collections.singletonList(new Component() {
            @NonNull
            @Override
            public Set<Permission> getRequiredPermissions() {
                return Collections.singleton(Jenkins.ADMINISTER);
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "JENKINS-58393 Test";
            }

            @Override
            public void addContents(@NonNull Container container) {
                container.add(new Content("test/testGenerateBundleExceptionHandler.md") {
                    @Override
                    public void writeTo(OutputStream os) throws IOException {
                        os.write("test".getBytes("UTF-8"));
                    }

                    @Override
                    public long getTime() throws IOException {
                        throw new IOException("JENKINS-58393: Exception should not fail the generation");
                    }
                });
            }
        });

        File bundleFile = temp.newFile();
        
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, componentsToCreate);
        }
        
        ZipFile zip = new ZipFile(bundleFile);
        Assert.assertNotNull(zip.getEntry("test/testGenerateBundleExceptionHandler.md"));
        Assert.assertNotNull(zip.getEntry("manifest.md"));
        Assert.assertNotNull(zip.getEntry("manifest/errors.txt"));
    }
}
