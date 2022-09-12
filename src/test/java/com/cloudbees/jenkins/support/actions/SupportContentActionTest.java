package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;

public class SupportContentActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    private final SupportContentAction action = new SupportContentAction(new SupportAction());

    /*
     * Trying to remove not existing bundles will do nothing, just a message in the log.
     */
    @Test
    public void deleteNotExistingBundleWillFail() throws IOException {
        String bundle = "../config.xml";
        logger.record(SupportContentAction.class, Level.FINE).capture(1);
        deleteBundle(bundle, "admin");
        assertTrue(logger.getMessages().stream()
            .anyMatch(m ->m.startsWith(String.format("The bundle selected %s does not exist", bundle))));
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
        assertThat(response.getContentAsString(), containsString(String.format("user is missing the %s/%s permission",
            Jenkins.ADMINISTER.group.title, Jenkins.ADMINISTER.name)));
        assertThat(response.getStatusCode(), equalTo(403));
    }

    @Test
    public void downloadOneBundleWillSucceed() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        logger.record(SupportContentAction.class, Level.FINE).capture(1);
        downloadBundle(bundle.getFileName().toString(), "admin", null);
        assertTrue(logger.getMessages().stream()
            .anyMatch(m -> m.startsWith(String.format("Bundle %s successfully downloaded", bundle))));
    }

    @Test
    public void downloadBundleWithoutPermissionWillFail() throws IOException {
        // Create a zip file as if it is a support bundle
        Path bundle = createFakeSupportBundle();
        assertTrue(Files.exists(bundle));
        WebResponse response = downloadBundle(bundle.getFileName().toString(), "user", null);
        assertThat(response.getContentAsString(), containsString(String.format("user is missing the %s/%s permission",
            Jenkins.ADMINISTER.group.title, Jenkins.ADMINISTER.name)));
        assertThat(response.getStatusCode(), equalTo(403));
    }

    @Test
    public void downloadMultiBundleWillSucceed() throws IOException {
        Path bundle = createFakeSupportBundle();
        Path bundle2 = createFakeSupportBundle();
        logger.record(SupportContentAction.class, Level.FINE).capture(1);
        logger.record(SupportContentAction.class, Level.FINE).capture(2);
        downloadBundle(bundle.getFileName().toString(), "admin", bundle2.getFileName().toString());
        assertTrue(logger.getMessages().stream().anyMatch(m -> m.endsWith("successfully downloaded")));
        assertTrue(logger.getMessages().stream().anyMatch(m -> m.startsWith("Temporary multiBundle file deleted")));
    }

    private Path createFakeSupportBundle() throws IOException {
        return Files.createTempFile(SupportPlugin.getRootDirectory().toPath(), "fake-bundle-", ".zip");
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
                json = "?json={%22bundles%22:[" +
                    "{%22selected%22:+true,%22name%22:+%22" + bundle + "%22}," +
                    "{%22selected%22:+true,%22name%22:+%22" + extraBundle + "%22}]}";
            }

            WebRequest request = new WebRequest(new URL(j.getURL() + this.action.getContextUrl()
                + "/" + action + json), HttpMethod.POST);
            return wc.getPage(request).getWebResponse();
        }
    }
}
