package com.cloudbees.jenkins.support;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.configfiles.ConfigFileComponent;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.impl.AboutUser;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.Label;
import hudson.model.Slave;
import hudson.util.RingBufferLogHandler;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class SupportActionTest {

    private JenkinsRule j;

    @TempDir
    private File temp;

    private SupportAction root;

    @BeforeEach
    void setUp(JenkinsRule j) {
        this.j = j;
        root = ExtensionList.lookupSingleton(SupportAction.class);
    }

    @Test
    void download() throws IOException, SAXException {
        downloadBundle("/download?json={\"components\":1}");
    }

    @Test
    void generateAllBundles() throws IOException, SAXException {
        downloadBundle("/generateAllBundles?json={\"components\":1}");
    }

    @Test
    void generateBundlesByUIButtonClick() throws IOException, SAXException, InterruptedException {
        ZipFile supportBundle = downloadSupportBundleByButtonClick();
        assertNotNull(supportBundle.getEntry("manifest.md"));
        assertNotNull(supportBundle.getEntry("browser.md"));
        assertNotNull(supportBundle.getEntry("user.md"));
        assertNotNull(supportBundle.getEntry("reverse-proxy.md"));
        assertNotNull(supportBundle.getEntry("nodes/master/logs/jenkins.log"));
    }

    @Test
    @Issue("JENKINS-63722")
    void generateAllBundlesBackwardCompatibility() throws Exception {
        Assumptions.assumeTrue(!Functions.isWindows());
        Assumptions.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());

        List<String> jvmSystemProcessMetricsFiles = Arrays.asList(
                "proc/meminfo.txt",
                "proc/self/status.txt",
                "proc/self/cmdline",
                "proc/self/environ",
                "proc/self/limits.txt",
                "proc/self/mountstats.txt");
        List<String> systemConfigurationFiles = Arrays.asList(
                "proc/swaps.txt",
                "proc/cpuinfo.txt",
                "proc/mounts.txt",
                "proc/system-uptime.txt",
                "proc/net/rpc/nfs.txt",
                "proc/net/rpc/nfsd.txt",
                "sysctl.txt",
                "dmesg.txt",
                "userid.txt",
                "dmi.txt");
        List<String> allFiles = Stream.of(jvmSystemProcessMetricsFiles, systemConfigurationFiles)
                .flatMap(Collection::stream)
                .toList();

        // Master should retrieve all files (backward compatibility)
        ZipFile zip = downloadBundle("/generateBundle?components=" + String.join(",", "Master"));
        assertBundleContains(
                zip, allFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));

        // MasterSystemConfiguration should retrieve only master system configuration files
        zip = downloadBundle("/generateBundle?components=" + String.join(",", "MasterSystemConfiguration"));
        assertBundleContains(
                zip,
                systemConfigurationFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));
        assertBundleNotContains(
                zip,
                jvmSystemProcessMetricsFiles.stream()
                        .map(s -> "nodes/master/" + s)
                        .collect(Collectors.toList()));

        // MasterJVMProcessSystemMetricsContents should retrieve only master JVM process files
        zip = downloadBundle("/generateBundle?components=" + String.join(",", "MasterJVMProcessSystemMetricsContents"));
        assertBundleContains(
                zip,
                jvmSystemProcessMetricsFiles.stream()
                        .map(s -> "nodes/master/" + s)
                        .collect(Collectors.toList()));
        assertBundleNotContains(
                zip,
                systemConfigurationFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));

        // MasterSystemConfiguration and MasterJVMProcessSystemMetricsContents should retrieve all agents files
        zip = downloadBundle("/generateBundle?components="
                + String.join(",", "MasterSystemConfiguration", "MasterJVMProcessSystemMetricsContents"));
        assertBundleContains(
                zip, allFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));

        j.createSlave("agent1", "test", null).getComputer().connect(false).get();

        // Agents should retrieve all agents files (backward compatibility)
        zip = downloadBundle("/generateBundle?components=" + String.join(",", "Agents"));
        assertBundleContains(
                zip, allFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));

        // AgentsSystemConfiguration should retrieve only agents system configuration files
        zip = downloadBundle("/generateBundle?components=" + String.join(",", "AgentsSystemConfiguration"));
        assertBundleContains(
                zip,
                systemConfigurationFiles.stream()
                        .map(s -> "nodes/slave/agent1/" + s)
                        .collect(Collectors.toList()));
        assertBundleNotContains(
                zip,
                jvmSystemProcessMetricsFiles.stream()
                        .map(s -> "nodes/slave/agent1/" + s)
                        .collect(Collectors.toList()));

        // AgentsJVMProcessSystemMetricsContents should retrieve only agents JVM process files
        zip = downloadBundle("/generateBundle?components=" + String.join(",", "AgentsJVMProcessSystemMetricsContents"));
        assertBundleContains(
                zip,
                jvmSystemProcessMetricsFiles.stream()
                        .map(s -> "nodes/slave/agent1/" + s)
                        .collect(Collectors.toList()));
        assertBundleNotContains(
                zip,
                systemConfigurationFiles.stream()
                        .map(s -> "nodes/slave/agent1/" + s)
                        .collect(Collectors.toList()));

        // AgentsSystemConfiguration and AgentsJVMProcessSystemMetricsContents should retrieve all agents files
        zip = downloadBundle("/generateBundle?components="
                + String.join(",", "AgentsSystemConfiguration", "AgentsJVMProcessSystemMetricsContents"));
        assertBundleContains(
                zip, allFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
    }

    /*
     * Trying to remove not existing bundles will do nothing, just a message in the log.
     */
    @Test
    void deleteNotExistingBundleWillFail() throws IOException {
        String bundle = "../config.xml";
        try (LogRecorder logger =
                new LogRecorder().record(SupportAction.class, Level.FINE).capture(1)) {
            deleteBundle(bundle, "admin");
            assertTrue(logger.getMessages().stream()
                    .anyMatch(m -> m.startsWith(String.format("The bundle selected %s does not exist", bundle))));
        }
    }

    /*
     * Trying to remove an existing bundle (a zip or log file in JH/support directory) will success.
     */
    @Test
    void deleteExistingBundleWillSucceed() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        deleteBundle(bundle.getFileName().toString(), "admin");
        assertTrue(Files.notExists(bundle));
    }

    @Test
    void deleteExistingBundleWithoutPermissionWillFail() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        WebResponse response = deleteBundle(bundle.getFileName().toString(), "user");
        assertThat(
                response.getContentAsString(),
                containsString(String.format(
                        "user is missing the %s/%s permission",
                        Jenkins.ADMINISTER.group.title, Jenkins.ADMINISTER.name)));
        assertThat(response.getStatusCode(), equalTo(403));
    }

    @Test
    void downloadOneBundleWillSucced() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        try (LogRecorder logger =
                new LogRecorder().record(SupportAction.class, Level.FINE).capture(1)) {
            downloadBundle(bundle.getFileName().toString(), "admin", null);
            assertTrue(logger.getMessages().stream()
                    .anyMatch(m -> m.startsWith(String.format("Bundle %s successfully downloaded", bundle))));
        }
    }

    @Test
    void downloadMultiBundleWillSucced() throws IOException {
        Path bundle = createFakeSupportBundle();
        Path bundle2 = createFakeSupportBundle();
        try (LogRecorder logger =
                new LogRecorder().record(SupportAction.class, Level.FINE).capture(2)) {
            downloadBundle(
                    bundle.getFileName().toString(),
                    "admin",
                    bundle2.getFileName().toString());
            assertTrue(logger.getMessages().stream().anyMatch(m -> m.endsWith("successfully downloaded")));
            assertTrue(logger.getMessages().stream().anyMatch(m -> m.startsWith("Temporary multiBundle file deleted")));
        }
    }

    private Path createFakeSupportBundle() throws IOException {
        Path parent = Files.createDirectories(SupportPlugin.getRootDirectory().toPath());
        return Files.createTempFile(parent, "fake-bundle-", ".zip");
    }

    /**
     * Download the bundle by requesting the <i>downloadBundle</i> page being logged in as user
     * @param bundle the bundle file to download
     * @param user the user logged in
     * @return the page
     * @throws IOException when any exception creating the url to call
     */
    private WebResponse downloadBundle(String bundle, String user, String extraBundle) throws IOException {
        return doBundle("downloadBundles", bundle, user, extraBundle);
    }

    /**
     * Delete the bundle by requesting the <i>deleteBundles</i> page being logged in as user
     * @param bundle the bundle file to delete
     * @param user the user logged in
     * @return the page
     * @throws IOException when any exception creating the url to call
     */
    private WebResponse deleteBundle(String bundle, String user) throws IOException {
        return doBundle("deleteBundles", bundle, user, null);
    }

    private WebResponse doBundle(String action, String bundle, String user, String extraBundle) throws IOException {
        j.jenkins.setCrumbIssuer(null);

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to("admin")
                .grant(Jenkins.READ)
                .everywhere()
                .to("user"));
        WebClient wc = j.createWebClient().withBasicCredentials(user).withThrowExceptionOnFailingStatusCode(false);

        String json = "?json={%22bundles%22:[{%22selected%22:+true,%22name%22:+%22" + bundle + "%22}]}";

        if (extraBundle != null) {
            json = "?json={%22bundles%22:[{%22selected%22:+true,%22name%22:+%22" + bundle
                    + "%22},{%22selected%22:+true,%22name%22:+%22" + extraBundle + "%22}]}";
        }

        WebRequest request =
                new WebRequest(new URL(j.getURL() + root.getUrlName() + "/" + action + json), HttpMethod.POST);
        return wc.getPage(request).getWebResponse();
    }

    private ZipFile downloadBundle(String s) throws IOException, SAXException {
        JenkinsRule.JSONWebResponse jsonWebResponse = j.postJSON(root.getUrlName() + s, "");
        File zipFile = File.createTempFile("test", "zip");
        IOUtils.copy(jsonWebResponse.getContentAsStream(), Files.newOutputStream(zipFile.toPath()));
        return new ZipFile(zipFile);
        // Zip file is valid
    }

    private ZipFile downloadSupportBundleByButtonClick() throws IOException, SAXException, InterruptedException {
        WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo(root.getUrlName());
        HtmlForm form = p.getFormByName("bundle-contents");
        HtmlButton submit = (HtmlButton) form.getElementsByTagName("button").get(0);
        HtmlPage page = submit.click();

        HtmlAnchor downloadLink = null;
        File zipFile;
        AtomicReference<HtmlPage> pageRef = new AtomicReference<>(page);

        HtmlPage finalPage = page;
        await().timeout(10, TimeUnit.SECONDS).until(() -> {
            try {
                return pageRef.get().getAnchorByText("click here") != null;
            } catch (ElementNotFoundException e) {
                pageRef.set((HtmlPage) pageRef.get().refresh());
                return false;
            }
        });

        downloadLink = pageRef.get().getAnchorByText("click here");
        if (downloadLink != null) {
            // Download the zip file
            WebResponse response = downloadLink.click().getWebResponse();
            zipFile = File.createTempFile("downloaded", ".zip");
            try (InputStream in = response.getContentAsStream()) {
                Files.copy(in, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            throw new IOException("Download link not found after " + 10 + " retries");
        }
        return new ZipFile(zipFile);
    }

    @Test
    void generateBundleFailsWhenNoParameter() {
        Exception exception = assertThrows(IOException.class, () -> downloadBundle("/generateBundle"));
        assertThat(exception.getMessage(), startsWith("Server returned HTTP response code: 400 for URL:"));
    }

    @Test
    void generateBundleWithSingleComponent() throws IOException, SAXException {
        ZipFile zip = downloadBundle("/generateBundle?components=" + componentIdsOf(ConfigFileComponent.class));
        assertNotNull(zip.getEntry("manifest.md"));
        assertNotNull(zip.getEntry("jenkins-root-configuration-files/config.xml"));
        assertEquals(2, zip.size());
    }

    @Test
    void generateBundleWith2Components() throws IOException, SAXException {
        ZipFile zip = downloadBundle(
                "/generateBundle?components=" + componentIdsOf(ConfigFileComponent.class, AboutUser.class));
        assertNotNull(zip.getEntry("manifest.md"));
        assertNotNull(zip.getEntry("jenkins-root-configuration-files/config.xml"));
        assertNotNull(zip.getEntry("user.md"));
        assertEquals(3, zip.size());
    }

    @SafeVarargs
    private static String componentIdsOf(Class<? extends Component>... components) {
        return Arrays.stream(components)
                .map(c -> ExtensionList.lookupSingleton(c).getId())
                .collect(Collectors.joining(","));
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     * <p>
     * If any warning is reported to j.u.l logger, treat that as a sign of failure, because
     * support-core plugin works darn hard to try to generate something in the presence of failing
     * {@link Component} impls.
     */
    @Test
    void takeSnapshotAndMakeSureSomethingHappens() throws Exception {
        j.createSlave("agent1", "test", null).getComputer().connect(false).get();
        j.createSlave("agent2", "test", null).getComputer().connect(false).get();

        RingBufferLogHandler checker = new RingBufferLogHandler(256);
        Logger logger = Logger.getLogger(SupportPlugin.class.getPackage().getName());

        logger.addHandler(checker);

        try {
            ZipFile z = downloadSupportBundleByButtonClick();

            // check the presence of files
            // TODO: emit some log entries and see if it gets captured here
            assertNotNull(z.getEntry("about.md"));
            assertNotNull(z.getEntry("nodes.md"));
            assertNotNull(z.getEntry("nodes/master/thread-dump.txt"));

            if (SystemPlatform.LINUX == SystemPlatform.current()) {
                List<String> files = Arrays.asList(
                        "proc/swaps.txt",
                        "proc/cpuinfo.txt",
                        "proc/mounts.txt",
                        "proc/system-uptime.txt",
                        "proc/net/rpc/nfs.txt",
                        "proc/net/rpc/nfsd.txt",
                        "proc/meminfo.txt",
                        "proc/self/status.txt",
                        "proc/self/cmdline",
                        "proc/self/environ",
                        "proc/self/limits.txt",
                        "proc/self/mountstats.txt",
                        "sysctl.txt",
                        "dmesg.txt",
                        "userid.txt",
                        "dmi.txt");

                for (String file : files) {
                    assertNotNull(z.getEntry("nodes/master/" + file), file + " was not found in the bundle");
                }
            }
        } finally {
            logger.removeHandler(checker);
            for (LogRecord r : checker.getView()) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    Throwable thrown = r.getThrown();
                    if (thrown != null) thrown.printStackTrace(System.err);

                    fail(r.getMessage());
                }
            }
        }
    }

    @Test
    void anonymizationSmokes() throws Exception {
        Slave node = j.createSlave(Label.get("super_secret_node"));
        File bundleFile = File.createTempFile("junit", null, temp);
        List<Component> componentsToCreate =
                Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(false);
            SupportPlugin.writeBundle(os, componentsToCreate);
            ZipFile zip = new ZipFile(bundleFile);
            String nodeComponentText =
                    IOUtils.toString(zip.getInputStream(zip.getEntry("nodes.md")), StandardCharsets.UTF_8);
            assertThat(
                    "Node name should be present when anonymization is disabled",
                    nodeComponentText,
                    containsString(node.getNodeName()));
        }
        bundleFile = File.createTempFile("junit", null, temp);
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(true);
            SupportPlugin.writeBundle(os, componentsToCreate);
            ZipFile zip = new ZipFile(bundleFile);
            String nodeComponentText =
                    IOUtils.toString(zip.getInputStream(zip.getEntry("nodes.md")), StandardCharsets.UTF_8);
            assertThat(
                    "Node name should not be present when anonymization is enabled",
                    nodeComponentText,
                    not(containsString(node.getNodeName())));
            String anonymousNodeName = ContentMappings.get().getMappings().get(node.getNodeName());
            assertThat(
                    "Anonymous node name should be present when anonymization is enabled",
                    nodeComponentText,
                    containsString(anonymousNodeName));
        }
    }

    /*
     * Check if the zip loses the folders due to anonymize '/' because there is an object with such a name. For example, a label.
     */
    @Test
    void corruptZipTestBySlash() throws Exception {
        String objectName = "agent-test";
        j.createSlave(objectName, "/", null);

        List<Component> componentsToCreate =
                Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));

        ZipFile zip = generateBundle(componentsToCreate, false);
        ZipFile anonymizedZip = generateBundle(componentsToCreate, true);

        bundlesMatch(
                zip,
                anonymizedZip,
                objectName,
                ContentMappings.get().getMappings().get(objectName));
    }

    /*
     * Check if the file names in the zip are corrupt due to anonymize '.' because there is an object with such a name.
     * For example, a label.
     */
    @Test
    void corruptZipTestByDot() throws Exception {
        String objectName = "agent-test";
        j.createSlave(objectName, ".", null);

        List<Component> componentsToCreate =
                Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));

        ZipFile zip = generateBundle(componentsToCreate, false);
        ZipFile anonymizedZip = generateBundle(componentsToCreate, true);

        bundlesMatch(
                zip,
                anonymizedZip,
                objectName,
                ContentMappings.get().getMappings().get(objectName));
    }

    /*
     * Check if the file names in the zip are corrupt due to anonymize words in the file names because there is an object
     * with such a name. For example, a label.
     */
    @Test
    void corruptZipTestByWordsInFileName() throws Exception {
        String objectName = "agent-test";
        // Create an agent with very bad words
        j.createSlave(objectName, "active plugins checksums md5 items about nodes manifest errors", null);

        /* This words are in the stopWords, so they won't never be replaced
        "jenkins", "node", "master", "computer", "item", "label", "view", "all", "unknown", "user", "anonymous",
        "authenticated", "everyone", "system", "admin", Jenkins.VERSION
        */

        List<Component> componentsToCreate =
                Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));

        ZipFile zip = generateBundle(componentsToCreate, false);
        ZipFile anonymizedZip = generateBundle(componentsToCreate, true);

        bundlesMatch(
                zip,
                anonymizedZip,
                objectName,
                ContentMappings.get().getMappings().get(objectName));
    }

    /**
     * Checks if both bundles have the same file names. It fails if it's not the case.
     * @param zip The bundle generated without anonymization
     * @param anonymizedZip The bundle generated with anonymization
     */
    private void bundlesMatch(ZipFile zip, ZipFile anonymizedZip, String objectName, String anonymizedObjectName) {
        // Print every entry
        List<String> entries = getFileNamesFromBundle(zip);

        // Debugging
        // entries.stream().forEach(entry -> System.out.println(entry));
        // System.out.println("nodes.md: \n"+ getContentZipEntry(zip,"nodes.md"));

        List<String> anonymizedEntries = getFileNamesFromBundle(anonymizedZip);

        // The name of the node created becomes replaced, so we change it to how the anonymization process has left it
        List<String> anonymizedEntriesRestored = anonymizedEntries.stream()
                .map(entry -> entry.replaceAll(anonymizedObjectName, objectName))
                .toList();

        // More debugging
        // System.out.println("Anonymized:");
        // entries.stream().forEach(entry -> System.out.println(entry));
        // System.out.println("nodes.md: \n"+ getContentZipEntry(zip,"nodes.md"));

        assertEquals(
                anonymizedEntriesRestored,
                entries,
                "Bundles should have the same files but it's not the case.\nBundle:\n " + entries + "\nAnonymized:\n "
                        + anonymizedEntriesRestored);
    }

    private ZipFile generateBundle(List<Component> componentsToCreate, boolean enabledAnonymization)
            throws IOException {
        File bundleFile = File.createTempFile("junit", null, temp);
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(enabledAnonymization);
            SupportPlugin.writeBundle(os, componentsToCreate);
            return new ZipFile(bundleFile);
        }
    }

    @NonNull
    private static List<String> getFileNamesFromBundle(ZipFile zip) {
        List<String> entries = new ArrayList<>();
        zip.stream().forEach(entry -> entries.add(entry.getName()));
        return entries;
    }

    /**
     * Check that the list of files passed in exist in the bundle.
     *
     * @param zip the bundle {@link ZipFile}
     * @param fileNames the list of files names
     */
    private static void assertBundleContains(ZipFile zip, Collection<String> fileNames) {
        for (String file : fileNames) {
            assertNotNull(zip.getEntry(file), file + " was not found in the bundle");
        }
    }

    /**
     * Check that the list of files passed in do not exist in the bundle.
     *
     * @param zip the bundle {@link ZipFile}
     * @param fileNames the list of files names
     */
    private static void assertBundleNotContains(ZipFile zip, Collection<String> fileNames) {
        for (String file : fileNames) {
            assertNull(zip.getEntry(file), file + " was found in the bundle");
        }
    }
}
