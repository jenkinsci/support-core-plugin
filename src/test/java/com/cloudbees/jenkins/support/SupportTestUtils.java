package com.cloudbees.jenkins.support;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractModelObject;
import hudson.model.Action;
import hudson.security.Permission;
import hudson.util.RingBufferLogHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlDivision;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.xml.sax.SAXException;

/**
 * Utility for helping to write tests.
 *
 * @author schristou88
 * @since 2.26
 */
public final class SupportTestUtils {

    private SupportTestUtils() {
        // hidden
    }

    /**
     * Invoke a component, and return the component contents as a String.
     */
    public static String invokeComponentToString(final Component component) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        component.addContents(new Container() {
            @Override
            public void add(@CheckForNull Content content) {
                try {
                    Objects.requireNonNull(content).writeTo(baos);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        return baos.toString();
    }

    /**
     * Invoke a component with {@link ContentFilter}, and return the component contents as a String.
     */
    public static String invokeComponentToString(final Component component, final ContentFilter filter)
            throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            component.addContents(new Container() {
                @Override
                public void add(@CheckForNull Content content) {
                    try {
                        ((PrefilteredContent) Objects.requireNonNull(content)).writeTo(baos, filter);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            return baos.toString();
        }
    }

    /**
     * Invoke a component, and return the component contents as a Map<String,String> where the key is the
     * zip key and the value is the string content.
     */
    public static Map<String, String> invokeComponentToMap(final Component component) {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Map<String, String> contents = new TreeMap<>();
        component.addContents(new Container() {
            @Override
            public void add(@CheckForNull Content content) {
                try {
                    Objects.requireNonNull(content).writeTo(baos);
                    contents.put(
                            SupportPlugin.getNameFiltered(
                                    SupportPlugin.getDefaultContentFilter(),
                                    content.getName(),
                                    content.getFilterableParameters()),
                            baos.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    baos.reset();
                }
            }
        });

        return contents;
    }

    /**
     * Invoke an object component, and return the component contents as a Map<String,String> where the key is the
     * zip key and the value is the string content.
     */
    public static <T extends AbstractModelObject> Map<String, String> invokeComponentToMap(
            final ObjectComponent<T> component, T object) {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Map<String, String> contents = new TreeMap<>();
        component.addContents(
                new Container() {
                    @Override
                    public void add(@CheckForNull Content content) {
                        try {
                            Objects.requireNonNull(content).writeTo(baos);
                            contents.put(
                                    SupportPlugin.getNameFiltered(
                                            SupportPlugin.getDefaultContentFilter(),
                                            content.getName(),
                                            content.getFilterableParameters()),
                                    baos.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            baos.reset();
                        }
                    }
                },
                object);

        return contents;
    }

    /**
     * Generate a support bundle via HTTPS using the action passed in.
     *
     * @param actionBaseUrl the base url of the action. "" for root actions.
     * @param action        the {@link Action}
     * @param webClient     a {@link org.jvnet.hudson.test.JenkinsRule.WebClient}
     * @return the {@link ZipFile} generated
     */
    public static ZipFile generateBundleFromAction(String actionBaseUrl, Action action, JenkinsRule.WebClient webClient)
            throws IOException, SAXException {

        HtmlPage p = webClient.goTo(actionBaseUrl + "/" + action.getUrlName());
        HtmlForm form = p.getFormByName("bundle-contents");
        HtmlButton submit = (HtmlButton) form.getElementsByTagName("button").get(0);
        Page zip = submit.click();
        File zipFile = File.createTempFile("test", "zip");
        IOUtils.copy(zip.getWebResponse().getContentAsStream(), Files.newOutputStream(zipFile.toPath()));
        return new ZipFile(zipFile);
    }

    /**
     * Generate a support bundle via HTTPS using the action passed in.
     *
     * <p>
     * If any warning is reported to j.u.l logger, treat that as a sign of failure, because
     * support-core plugin works darn hard to try to generate something in the presence of failing
     * {@link Component} implementations.
     *
     * @param baseUrl     the base url of the action. "" for root actions.
     * @param action      the {@link Action}
     * @param loggerClass the class package to inspect loggers from
     * @param webClient   a {@link org.jvnet.hudson.test.JenkinsRule.WebClient}
     * @return the {@link ZipFile} generated
     */
    public static ZipFile generateBundleWithoutWarnings(
            String baseUrl, Action action, Class<?> loggerClass, JenkinsRule.WebClient webClient) throws Exception {

        RingBufferLogHandler checker = new RingBufferLogHandler(256);
        Logger logger = Logger.getLogger(loggerClass.getPackage().getName());
        logger.addHandler(checker);

        try {
            return SupportTestUtils.generateBundleFromAction(baseUrl, action, webClient);
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

    /**
     * Set up a {@link hudson.security.SecurityRealm} and {@link hudson.security.AuthorizationStrategy} with two users:
     * * one "privileged" user with required permissions
     * * one "unprivileged" user Overall/Read and optionally a set of "test" permissions
     *
     * @param j the {@link JenkinsRule}
     * @param userUnprivileged the id of the unprivileged user
     * @param userPrivileged the id of the privileged user
     * @param requiredPermissions the set of required permissions given to the unprivileged user
     * @param testPermissions the set of test permissions given to the privileged user
     */
    private static void setupAuth(
            JenkinsRule j,
            String userUnprivileged,
            String userPrivileged,
            Set<Permission> requiredPermissions,
            Set<Permission> testPermissions) {

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
                .grant(Jenkins.READ)
                .everywhere()
                .to(userUnprivileged)
                .grant(Jenkins.READ)
                .everywhere()
                .to(userPrivileged);
        requiredPermissions.forEach(
                permission -> auth.grant(permission).everywhere().to(userPrivileged));
        testPermissions.forEach(
                permission -> auth.grant(permission).everywhere().to(userUnprivileged));
        j.jenkins.setAuthorizationStrategy(auth);
    }

    /**
     * Check that only a privileged user (with the required permissions) can see an Action link.
     *
     * @param j the {@link JenkinsRule}
     * @param baseUrl the base URL of the action
     * @param action the {@link Action}
     * @param requiredPermissions the set of required permissions to see this action
     * @param testPermissions a set of test permissions (to test that those permissions are not enough to see the action link)
     * @throws Exception webclient failures
     */
    public static void testPermissionToSeeAction(
            JenkinsRule j,
            String baseUrl,
            Action action,
            Set<Permission> requiredPermissions,
            Set<Permission> testPermissions)
            throws Exception {

        String userUnprivileged = "underprivileged";
        String userPrivileged = "privileged";

        setupAuth(j, userUnprivileged, userPrivileged, requiredPermissions, testPermissions);

        JenkinsRule.WebClient wc = j.createWebClient();

        {
            wc.login(userUnprivileged);
            HtmlPage page = wc.goTo(baseUrl);
            HtmlDivision sidePanel = (HtmlDivision) page.getElementById("side-panel");
            assertTrue(
                    sidePanel
                            .getElementsByAttribute("a", "title", action.getDisplayName())
                            .isEmpty(),
                    userUnprivileged + " should not be able to see the Support action");
        }

        {
            wc.login(userPrivileged);
            HtmlPage page = wc.goTo(baseUrl);
            HtmlDivision sidePanel = (HtmlDivision) page.getElementById("side-panel");
            assertEquals(
                    1,
                    sidePanel.getElementsByTagName("a").stream()
                            .filter(htmlElement -> htmlElement.getTextContent().equals(action.getDisplayName()))
                            .count(),
                    userPrivileged + " should be able to see the Support action");
        }
    }

    /**
     * Check that only a privileged user (with the required permissions) can display an Action page.
     *
     * @param j the {@link JenkinsRule}
     * @param baseUrl the base URL of the action
     * @param action the {@link Action}
     * @param requiredPermissions the set of required permissions to see this action
     * @param testPermissions a set of test permissions (to test that those permissions are not enough to see the action link)
     * @throws Exception webclient failures
     */
    public static void testPermissionToDisplayAction(
            JenkinsRule j,
            String baseUrl,
            Action action,
            Set<Permission> requiredPermissions,
            Set<Permission> testPermissions)
            throws Exception {

        String userUnprivileged = "underprivileged";
        String userPrivileged = "privileged";

        setupAuth(j, userUnprivileged, userPrivileged, requiredPermissions, testPermissions);

        JenkinsRule.WebClient wc = j.createWebClient();

        {
            wc.login(userUnprivileged);
            assertThrows(
                    FailingHttpStatusCodeException.class,
                    () -> wc.withThrowExceptionOnFailingStatusCode(true).goTo(baseUrl + "/" + action.getUrlName()),
                    userUnprivileged + " should not be able to display the Support action page");
        }

        {
            wc.login(userPrivileged);
            assertNotNull(
                    wc.withThrowExceptionOnFailingStatusCode(true).goTo(baseUrl + "/" + action.getUrlName()),
                    userPrivileged + " should be able to display the Support action page");
        }
    }

    /**
     * Return if this instance if running Java 8 or a lower version
     * (Can be replaced by JavaUtils.isRunningWithJava8OrBelow() since 2.164.1)
     * @return true if running java 8 or an older version
     */
    public static boolean isJava8OrBelow() {
        return System.getProperty("java.specification.version").startsWith("1.");
    }
}
