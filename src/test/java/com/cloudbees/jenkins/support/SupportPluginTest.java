package com.cloudbees.jenkins.support;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.impl.BuildQueue;
import com.cloudbees.jenkins.support.impl.SystemProperties;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.security.Permission;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SupportPluginTest {

    @TempDir
    private File temp;

    @Test
    @Issue("JENKINS-63722")
    void testComponentIdsAreUnique(JenkinsRule j) {
        // If component IDs have duplicate, the Set size should be different because it only add an item if it is unique
        assertEquals(
                SupportPlugin.getComponents().stream()
                        .map(Component::getId)
                        .collect(Collectors.toSet())
                        .size(),
                SupportPlugin.getComponents().size(),
                "Components ids should be unique");
    }

    @Test
    @Issue("JENKINS-58393")
    void testGenerateBundleExceptionHandler(JenkinsRule j) throws Exception {
        List<Component> componentsToCreate = Arrays.asList(
                new Component() {
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
                                os.write("test".getBytes(StandardCharsets.UTF_8));
                            }

                            @Override
                            public long getTime() throws IOException {
                                throw new IOException("JENKINS-58393: Exception should not fail the generation");
                            }
                        });
                    }
                },
                ExtensionList.lookup(Component.class).get(AboutJenkins.class),
                ExtensionList.lookup(Component.class).get(BuildQueue.class),
                ExtensionList.lookup(Component.class).get(SystemProperties.class));

        File bundleFile = File.createTempFile("junit", null, temp);

        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, componentsToCreate);
        }

        ZipFile zip = new ZipFile(bundleFile);
        assertNull(zip.getEntry("test/testGenerateBundleExceptionHandler.md"));
        assertNotNull(zip.getEntry("manifest.md"));
        assertNotNull(zip.getEntry("manifest/errors.txt"));
        assertNotNull(zip.getEntry("buildqueue.md"));
        assertNotNull(zip.getEntry("nodes/master/system.properties"));
        assertNotNull(zip.getEntry("about.md"));
        assertNotNull(zip.getEntry("nodes.md"));
    }

    /**
     * Test that a component can supersede another component.
     * We are superseding AboutJenkins and BuildQueue components.
     * even if is added to the list of components to create.
     * It will not add its files to the bundle
     */
    @Test
    void testSupersedesComponent(JenkinsRule j) throws Exception {
        List<Component> components = List.of(
                new Component() {
                    @NonNull
                    @Override
                    public Set<Permission> getRequiredPermissions() {
                        return Collections.singleton(Jenkins.ADMINISTER);
                    }

                    @NonNull
                    @Override
                    public String getDisplayName() {
                        return "Test Component";
                    }

                    @Override
                    public boolean supersedes(Component component) {
                        return component instanceof AboutJenkins || component instanceof BuildQueue;
                    }

                    @Override
                    public void addContents(@NonNull Container container) {
                        container.add(new Content("test/testWriteBundleWithJenkinsRule.md") {
                            @Override
                            public void writeTo(OutputStream os) throws IOException {
                                os.write("test content".getBytes(StandardCharsets.UTF_8));
                            }
                        });
                    }
                },
                ExtensionList.lookupSingleton(AboutJenkins.class),
                ExtensionList.lookupSingleton(BuildQueue.class),
                ExtensionList.lookupSingleton(SystemProperties.class));

        File bundleFile = File.createTempFile("junit", null, temp);
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, components);
        }

        ZipFile zip = new ZipFile(bundleFile);
        assertNotNull(zip.getEntry("test/testWriteBundleWithJenkinsRule.md"));
        assertNotNull(zip.getEntry("manifest.md"));
        assertNotNull(zip.getEntry("nodes/master/system.properties"));

        // Assert null for AboutJenkins.class, BuildQueue.class components
        assertNull(zip.getEntry("buildqueue.md"));
        assertNull(zip.getEntry("about.md"));
        assertNull(zip.getEntry("nodes.md"));
    }
}
