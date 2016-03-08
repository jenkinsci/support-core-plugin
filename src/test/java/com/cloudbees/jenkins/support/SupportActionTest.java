package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.util.IOUtils;
import hudson.util.RingBufferLogHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import javax.inject.Inject;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * @author Kohsuke Kawaguchi
 */
public class SupportActionTest extends Assert {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Inject
    SupportAction root;

    @Before
    public void setUp() {
        rule.jenkins.getInjector().injectMembers(this);
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
            IOUtils.copy(zip.getWebResponse().getContentAsStream(), zipFile);

            ZipFile z = new ZipFile(zipFile);

            // check the presence of files
            // TODO: emit some log entries and see if it gets captured here
            assertNotNull(z.getEntry("about.md"));
            assertNotNull(z.getEntry("nodes.md"));
            assertNotNull(z.getEntry("nodes/slave/slave1/system.properties"));
            assertNotNull(z.getEntry("nodes/master/thread-dump.txt"));
            assertNotNull(z.getEntry("nodes/slave/slave2/launchLogs/slave.log"));
        } finally {
            logger.removeHandler(checker);
            for (LogRecord r : checker.getView()) {
                if (r.getLevel().intValue() >= Level.WARNING.intValue())
                    fail(r.getMessage());
            }
        }
    }
}
