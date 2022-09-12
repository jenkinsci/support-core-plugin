package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.impl.BuildQueue;
import com.cloudbees.jenkins.support.impl.SystemProperties;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.Slave;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SupportPluginTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    
    @Test
    @Issue("JENKINS-63722")
    public void testComponentIdsAreUnique() {
        // If component IDs have duplicate, the Set size should be different because it only add an item if it is unique
        assertEquals("Components ids should be unique", 
            SupportPlugin.getComponents().stream().map(Component::getId).collect(Collectors.toSet()).size(),
            SupportPlugin.getComponents().stream().map(Component::getId).count()
            );
    }

    @Test
    @Issue("JENKINS-58393")
    public void testGenerateBundleExceptionHandler() throws Exception {
        List<Component> componentsToCreate = Arrays.asList(new Component() {
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
                ExtensionList.lookup(Component.class).get(SystemProperties.class)
        );

        File bundleFile = temp.newFile();

        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, componentsToCreate);
        }

        try(ZipFile zip = new ZipFile(bundleFile)) {
            assertNull(zip.getEntry("test/testGenerateBundleExceptionHandler.md"));
            assertNotNull(zip.getEntry("manifest.md"));
            assertNotNull(zip.getEntry("manifest/errors.txt"));
            assertNotNull(zip.getEntry("buildqueue.md"));
            assertNotNull(zip.getEntry("nodes/master/system.properties"));
            assertNotNull(zip.getEntry("about.md"));
            assertNotNull(zip.getEntry("nodes.md"));
        }
    }

    @Test
    public void anonymizationSmokes() throws Exception {
        Slave node = j.createSlave(Label.get("super_secret_node"));
        File bundleFile = temp.newFile();
        List<Component> componentsToCreate =
            Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(false);
            SupportPlugin.writeBundle(os, componentsToCreate);
            try (ZipFile zip = new ZipFile(bundleFile)) {
                String nodeComponentText =
                    IOUtils.toString(zip.getInputStream(zip.getEntry("nodes.md")), StandardCharsets.UTF_8);
                assertThat("Node name should be present when anonymization is disabled",
                    nodeComponentText, containsString(node.getNodeName()));
            }
        }
        bundleFile = temp.newFile();
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(true);
            SupportPlugin.writeBundle(os, componentsToCreate);
            try (ZipFile zip = new ZipFile(bundleFile)) {
                String nodeComponentText =
                    IOUtils.toString(zip.getInputStream(zip.getEntry("nodes.md")), StandardCharsets.UTF_8);
                assertThat("Node name should not be present when anonymization is enabled",
                    nodeComponentText, not(containsString(node.getNodeName())));
                String anonymousNodeName = ContentMappings.get().getMappings().get(node.getNodeName());
                assertThat("Anonymous node name should be present when anonymization is enabled",
                    nodeComponentText, containsString(anonymousNodeName));
            }
        }
    }

    /**
     * Check if the zip loses the folders due to anonymize '/' because there is an object with such a name.
     * For example, a label.
     */
    @Test
    public void corruptZipTestBySlash() throws Exception {
        String objectName = "agent";
        j.createSlave(objectName, "/", null);

        List<Component> componentsToCreate =
            Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));

        ZipFile zip = generateBundle(componentsToCreate, false);
        ZipFile anonymizedZip = generateBundle(componentsToCreate, true);

        bundlesMatch(zip, anonymizedZip, objectName, ContentMappings.get().getMappings().get(objectName));
    }

    /**
     * Check if the file names in the zip are corrupt due to anonymize '.' because there is an object with such a name.
     * For example, a label.
     */
    @Test
    public void corruptZipTestByDot() throws Exception {
        String objectName = "agent";
        j.createSlave(objectName, ".", null);

        List<Component> componentsToCreate =
            Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));

        ZipFile zip = generateBundle(componentsToCreate, false);
        ZipFile anonymizedZip = generateBundle(componentsToCreate, true);

        bundlesMatch(zip, anonymizedZip, objectName, ContentMappings.get().getMappings().get(objectName));
    }

    /**
     * Check if the file names in the zip are corrupt due to anonymize words in the file names because there is an object
     * with such a name. For example, a label.
     */
    @Test
    public void corruptZipTestByWordsInFileName() throws Exception {
        String objectName = "agent";
        // Create an agent with very bad words
        j.createSlave(objectName, "active plugins checksums md5 items about nodes manifest errors", null);

        /* These words are in the stopWords, so they will never be replaced
        "jenkins", "node", "master", "computer", "item", "label", "view", "all", "unknown", "user", "anonymous",
        "authenticated", "everyone", "system", "admin", Jenkins.VERSION
        */

        List<Component> componentsToCreate =
            Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));

        ZipFile zip = generateBundle(componentsToCreate, false);
        ZipFile anonymizedZip = generateBundle(componentsToCreate, true);

        bundlesMatch(zip, anonymizedZip, objectName, ContentMappings.get().getMappings().get(objectName));
    }

    private ZipFile generateBundle(List<Component> componentsToCreate, boolean enabledAnonymization) throws IOException {
        File bundleFile = temp.newFile();
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(enabledAnonymization);
            SupportPlugin.writeBundle(os, componentsToCreate);
            return new ZipFile(bundleFile);
        }
    }

    /**
     * Checks if both bundles have the same file names. It fails if it's not the case.
     * @param zip The bundle generated without anonymization
     * @param anonymizedZip The bundle generated with anonymization
     */
    private void bundlesMatch(ZipFile zip, ZipFile anonymizedZip, String objectName, String anonymizedObjectName) {
        // Print every entry
        List<String> entries = getFileNamesFromBundle(zip);

        List<String> anonymizedEntries = getFileNamesFromBundle(anonymizedZip);

        //The name of the node created becomes replaced, so we change it to how the anonymization process has left it
        List<String> anonymizedEntriesRestored = anonymizedEntries.stream()
            .map(entry -> entry.replaceAll(anonymizedObjectName, objectName))
            .collect(Collectors.toList());

        assertEquals("Bundles should have the same files but it's not the case.\nBundle:\n "
            + entries + "\nAnonymized:\n " + anonymizedEntriesRestored, anonymizedEntriesRestored, entries);
    }

    @NonNull
    private List<String> getFileNamesFromBundle(ZipFile zip) {
        List<String> entries = new ArrayList<>();
        zip.stream().forEach(entry -> entries.add(entry.getName()));
        return entries;
    }
}
