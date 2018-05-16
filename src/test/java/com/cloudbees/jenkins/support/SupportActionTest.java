package com.cloudbees.jenkins.support;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.Slave;
import hudson.util.RingBufferLogHandler;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

/**
 * @author Kohsuke Kawaguchi
 */
public class SupportActionTest {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Inject
    SupportAction root;

    @Before
    public void setUp() {
        rule.jenkins.getInjector().injectMembers(this);
    }

    @Test
    public void download() throws IOException, SAXException {
        downloadBundle("/download?json={\"components\":1}");
    }

    @Test
    public void generateAllBundles() throws IOException, SAXException {
        downloadBundle("/generateAllBundles?json={\"components\":1}");
    }

    private void downloadBundle(String s) throws IOException, SAXException {
        JenkinsRule.JSONWebResponse jsonWebResponse = rule.postJSON(root.getUrlName() + s, "");
        File zipFile = File.createTempFile("test", "zip");
        IOUtils.copy(jsonWebResponse.getContentAsStream(), Files.newOutputStream(zipFile.toPath()));
        ZipFile z = new ZipFile(zipFile);
        // Zip file is valid
    }

    /**
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     *
     * <p>
     * If any warning is reported to j.u.l logger, treat that as a sign of failure, because
     * support-core plugin works darn hard to try to generate something in the presence of failing
     * {@link Component} impls.
     */
    @Test
    public void takeSnapshotAndMakeSureSomethingHappens() throws Exception {
        rule.createSlave("slave1","test",null).getComputer().connect(false).get();
        rule.createSlave("slave2","test",null).getComputer().connect(false).get();

        RingBufferLogHandler checker = new RingBufferLogHandler();
        Logger logger = Logger.getLogger(SupportPlugin.class.getPackage().getName());

        logger.addHandler(checker);

        try {
            WebClient wc = rule.createWebClient();
            HtmlPage p = wc.goTo(root.getUrlName());

            HtmlForm form = p.getFormByName("bundle-contents");
            HtmlButton submit = (HtmlButton) form.getHtmlElementsByTagName("button").get(0);
            Page zip = submit.click();
            File zipFile = File.createTempFile("test", "zip");
            IOUtils.copy(zip.getWebResponse().getContentAsStream(), Files.newOutputStream(zipFile.toPath()));

            ZipFile z = new ZipFile(zipFile);

            // check the presence of files
            // TODO: emit some log entries and see if it gets captured here
            assertNotNull(z.getEntry("about.md"));
            assertNotNull(z.getEntry("nodes.md"));
            assertNotNull(z.getEntry("nodes/master/thread-dump.txt"));

            if (SystemPlatform.LINUX == SystemPlatform.current()) {
                List<String> files = Arrays.asList("proc/swaps.txt",
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
                    assertNotNull(file +" was not found in the bundle",
                                  z.getEntry("nodes/master/"+file));
                }
            }
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

    @Test
    public void anonymizationSmokes() throws Exception {
        Slave node = rule.createSlave(Label.get("super_secret_node"));
        File bundleFile = temp.newFile();
        List<Component> componentsToCreate = Collections.singletonList(ExtensionList.lookup(Component.class).get(AboutJenkins.class));
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(false);
            SupportPlugin.writeBundle(os, componentsToCreate);
            ZipFile zip = new ZipFile(bundleFile);
            String nodeComponentText = IOUtils.toString(zip.getInputStream(zip.getEntry("nodes.md")), StandardCharsets.UTF_8);
            assertThat("Node name should be present when anonymization is disabled",
                    nodeComponentText, containsString(node.getNodeName()));
        }
        bundleFile = temp.newFile();
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(true);
            SupportPlugin.writeBundle(os, componentsToCreate);
            ZipFile zip = new ZipFile(bundleFile);
            String nodeComponentText = IOUtils.toString(zip.getInputStream(zip.getEntry("nodes.md")), StandardCharsets.UTF_8);
            assertThat("Node name should not be present when anonymization is enabled",
                    nodeComponentText, not(containsString(node.getNodeName())));
            String anonymousNodeName = ContentMappings.get().getMappings().get(node.getNodeName());
            assertThat("Anonymous node name should be present when anonymization is enabled",
                    nodeComponentText, containsString(anonymousNodeName));
        }
    }
}
