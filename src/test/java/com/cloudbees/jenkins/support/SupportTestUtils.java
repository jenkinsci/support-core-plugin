package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractModelObject;
import hudson.model.Action;
import hudson.util.RingBufferLogHandler;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import static org.junit.Assert.fail;

/**
 * Utility for helping to write tests.
 *
 * @author schristou88
 * @since 2.26
 */
public class SupportTestUtils {

    /**
     * Invoke a component, and return the component contents as a String.
     */
    public static String invokeComponentToString(final Component component) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        component.addContents(
                new Container() {
                    @Override
                    public void add(@CheckForNull Content content) {
                        try {
                            content.writeTo(baos);
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
    public static String invokeComponentToString(final Component component, final ContentFilter filter) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            component.addContents(
                    new Container() {
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
     * Invoke an aobject component, and return the component contents as a String.
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
                            content.writeTo(baos);
                            contents.put(
                                    SupportPlugin.getNameFiltered(
                                            SupportPlugin.getContentFilter(), 
                                            content.getName(), 
                                            content.getFilterableParameters()
                                    ), 
                                    baos.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            baos.reset();
                        }
                    }
                }, object);

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
    public static ZipFile generateBundleFromAction(String actionBaseUrl, Action action,
                                                   JenkinsRule.WebClient webClient) throws IOException, SAXException {

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
    public static ZipFile generateBundleWithoutWarnings(String baseUrl,
                                                        Action action,
                                                        Class<?> loggerClass,
                                                        JenkinsRule.WebClient webClient) throws Exception {

        RingBufferLogHandler checker = new RingBufferLogHandler(256);
        Logger logger = Logger.getLogger(loggerClass.getPackage().getName());
        logger.addHandler(checker);

        try {
            return SupportTestUtils.generateBundleFromAction(baseUrl, action, webClient
            );
        } finally {
            logger.removeHandler(checker);
            for (LogRecord r : checker.getView()) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                    Throwable thrown = r.getThrown();
                    if (thrown != null)
                        thrown.printStackTrace(System.err);
                    fail(r.getMessage());
                }
            }
        }
    }
}