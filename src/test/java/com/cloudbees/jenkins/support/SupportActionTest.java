package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.actions.SupportContentAction;
import com.cloudbees.jenkins.support.configfiles.ConfigFileComponent;
import com.cloudbees.jenkins.support.impl.AboutUser;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Kohsuke Kawaguchi
 */
public class SupportActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Inject
    SupportAction root;

    @Before
    public void setUp() {
        Objects.requireNonNull(j.jenkins.getInjector()).injectMembers(this);
    }

    @Test
    public void download() throws IOException, SAXException {
        downloadBundle("/download?json={\"components\":1}").close();
    }

    @Test
    public void generateAllBundles() throws IOException, SAXException {
        downloadBundle("/generateAllBundles?json={\"components\":1}").close();
    }

    /*
     * Trying to remove not existing bundles will do nothing, just a message in the log.
     */
    @Test
    public void deleteNotExistingBundleWillFail() throws IOException, SAXException {
        String bundle = "../config.xml";
        logger.record(SupportContentAction.class, Level.FINE).capture(1);
        deleteBundle(bundle, "admin");
        assertTrue(logger.getMessages().stream().anyMatch(m -> m.startsWith(String.format("The file selected %s does not exist", bundle))));
    }

    /*
     * Trying to remove an existing bundle (a zip or log file in JH/support directory) will success.
     */
    @Test
    public void deleteExistingBundleWillSucceed() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        deleteBundle(bundle.getFileName().toString(), "admin");
        assertTrue(Files.notExists(bundle));
    }

    @Test
    public void deleteExistingBundleWithoutPermissionWillFail() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        WebResponse response = deleteBundle(bundle.getFileName().toString(), "user");
        assertThat(response.getContentAsString(), containsString(String.format("user is missing the %s/%s permission", Jenkins.ADMINISTER.group.title, Jenkins.ADMINISTER.name)));
        assertThat(response.getStatusCode(), equalTo(403));
    }

    @Test
    public void downloadOneBundleWillSucceed() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        logger.record(SupportContentAction.class, Level.FINE).capture(1);
        downloadBundle(bundle.getFileName().toString(), "admin", null);
        assertTrue(logger.getMessages().stream().anyMatch(m -> m.startsWith(String.format("Bundle %s successfully downloaded", bundle))));
    }

    @Test
    public void downloadMultiBundleWillSucceed() throws IOException {
        Path bundle = createFakeSupportBundle();
        Path bundle2 = createFakeSupportBundle();
        logger.record(SupportContentAction.class, Level.FINE).capture(2);
        downloadBundle(bundle.getFileName().toString(), "admin", bundle2.getFileName().toString());
        assertTrue(logger.getMessages().stream().anyMatch(m -> m.endsWith("successfully downloaded")));
        assertTrue(logger.getMessages().stream().anyMatch(m -> m.startsWith("Temporary multiBundle file deleted")));
    }

    private Path createFakeSupportBundle() throws IOException {
        Path parent = Files.createDirectories(SupportPlugin.getRootDirectory().toPath());
        return Files.createTempFile(parent, "fake-bundle-", ".zip");
    }

    /**
     * Download the bundle by requesting the <i>downloadBundle</i> page being logged in as user
     *
     * @param bundle the bundle file to download
     * @param user   the user logged in
     * @throws IOException when any exception creating the url to call
     */
    private void downloadBundle(String bundle, String user, String extraBundle) throws IOException {
        doBundle("downloadBundles", bundle, user, extraBundle);
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
        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy()
                        .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                        .grant(Jenkins.READ).everywhere().to("user")
        );
        try(WebClient wc = j.createWebClient()
                .withBasicCredentials(user)
                .withThrowExceptionOnFailingStatusCode(false)) {

            String json = "?json={%22bundles%22:[{%22selected%22:+true,%22name%22:+%22" + bundle + "%22}]}";

            if (extraBundle != null) {
                json = "?json={%22bundles%22:[{%22selected%22:+true,%22name%22:+%22" + bundle + "%22},{%22selected%22:+true,%22name%22:+%22" + extraBundle + "%22}]}";
            }

            WebRequest request = new WebRequest(new URL(j.getURL() + root.getUrlName() + "/" + action + json), HttpMethod.POST);
            return wc.getPage(request).getWebResponse();
        }
    }

    private ZipFile downloadBundle(String s) throws IOException, SAXException {
        JenkinsRule.JSONWebResponse jsonWebResponse = j.postJSON(root.getUrlName() + s, "");
        File zipFile = File.createTempFile("test", "zip");
        IOUtils.copy(jsonWebResponse.getContentAsStream(), Files.newOutputStream(zipFile.toPath()));
        return new ZipFile(zipFile);
        // Zip file is valid
    }

    @Test
    public void generateBundleFailsWhenNoParameter() {
        Exception exception = assertThrows(IOException.class, () -> downloadBundle("/generateBundle").close());
        assertThat(exception.getMessage(), startsWith("Server returned HTTP response code: 400 for URL:"));
    }

    @Test
    public void generateBundleWithSingleComponent() throws IOException, SAXException {
        try(ZipFile zip = downloadBundle("/generateBundle?components="+ SupportTestUtils.componentIdsOf(ConfigFileComponent.class))) {
            assertNotNull(zip.getEntry("manifest.md"));
            assertNotNull(zip.getEntry("jenkins-root-configuration-files/config.xml"));
            assertEquals(2, zip.size());
        }
    }

    @Test
    public void generateBundleWith2Components() throws IOException, SAXException {
        try(ZipFile zip = downloadBundle("/generateBundle?components=" + SupportTestUtils.componentIdsOf(ConfigFileComponent.class, AboutUser.class))) {
            assertNotNull(zip.getEntry("manifest.md"));
            assertNotNull(zip.getEntry("jenkins-root-configuration-files/config.xml"));
            assertNotNull(zip.getEntry("user.md"));
            assertEquals(3, zip.size());
        }
    }
}

