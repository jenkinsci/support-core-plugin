package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.configfiles.ConfigFileComponent;
import com.cloudbees.jenkins.support.impl.AboutUser;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Functions;
import hudson.util.RingBufferLogHandler;
import org.apache.commons.io.IOUtils;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.LoggerRule;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class SupportBundleActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public LoggerRule logger = new LoggerRule();

    private final SupportBundleAction action = new SupportBundleAction(new SupportAction());

    @Test
    public void generateAllBundles() throws IOException, SAXException {
        downloadBundle("/generateAllBundles?json={\"components\":1}").close();
    }

    @Test
    @Issue("JENKINS-63722")
    public void generateAllBundlesBackwardCompatibility() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());
        Assume.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());

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
            .flatMap(Collection::stream).collect(Collectors.toList());

        // Master should retrieve all files (backward compatibility)
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "Master"))) {
            assertBundleContains(zip, allFiles.stream().map(s -> "nodes/master/"+s).collect(Collectors.toList()));
        }

        // MasterSystemConfiguration should retrieve only master system configuration files
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "MasterSystemConfiguration"))) {
        assertBundleContains(zip,
            systemConfigurationFiles.stream().map(s -> "nodes/master/"+s).collect(Collectors.toList()));
        assertBundleNotContains(zip,
            jvmSystemProcessMetricsFiles.stream().map(s -> "nodes/master/"+s).collect(Collectors.toList()));
        }

        // MasterJVMProcessSystemMetricsContents should retrieve only master JVM process files
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "MasterJVMProcessSystemMetricsContents"))) {
            assertBundleContains(zip,
                jvmSystemProcessMetricsFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));
            assertBundleNotContains(zip,
                systemConfigurationFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));
        }

        // MasterSystemConfiguration and MasterJVMProcessSystemMetricsContents should retrieve all agents files
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "MasterSystemConfiguration", "MasterJVMProcessSystemMetricsContents"))) {
            assertBundleContains(zip, allFiles.stream().map(s -> "nodes/master/" + s).collect(Collectors.toList()));
        }

        Objects.requireNonNull(j.createSlave("agent1", "test", null).getComputer()).connect(false).get();

        // Agents should retrieve all agents files (backward compatibility)
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "Agents"))) {
            assertBundleContains(zip, allFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
        }

        // AgentsSystemConfiguration should retrieve only agents system configuration files
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "AgentsSystemConfiguration"))) {
            assertBundleContains(zip,
                systemConfigurationFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
            assertBundleNotContains(zip,
                jvmSystemProcessMetricsFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
        }

        // AgentsJVMProcessSystemMetricsContents should retrieve only agents JVM process files
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "AgentsJVMProcessSystemMetricsContents"))) {
            assertBundleContains(zip,
                jvmSystemProcessMetricsFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
            assertBundleNotContains(zip,
                systemConfigurationFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
        }
        
        // AgentsSystemConfiguration and AgentsJVMProcessSystemMetricsContents should retrieve all agents files
        try(ZipFile zip = downloadBundle("/generateBundle?components="
            + String.join(",", "AgentsSystemConfiguration", "AgentsJVMProcessSystemMetricsContents"))) {
            assertBundleContains(zip, allFiles.stream().map(s -> "nodes/slave/agent1/" + s).collect(Collectors.toList()));
        }
    }

    private ZipFile downloadBundle(String s) throws IOException, SAXException {
        JenkinsRule.JSONWebResponse jsonWebResponse = j.postJSON(action.getContextUrl() + s, "");
        File zipFile = File.createTempFile("test", "zip");
        IOUtils.copy(jsonWebResponse.getContentAsStream(), Files.newOutputStream(zipFile.toPath()));
        return new ZipFile(zipFile);
    }

    @Test
    public void generateBundleFailsWhenNoParameter() {
        Exception exception = assertThrows(IOException.class, () -> downloadBundle("/generateBundle").close());
        assertThat(exception.getMessage(), startsWith("Server returned HTTP response code: 400 for URL:"));
    }

    @Test
    public void generateBundleWithSingleComponent() throws IOException, SAXException {
        try(ZipFile zip = downloadBundle("/generateBundle?components="+
            SupportTestUtils.componentIdsOf(ConfigFileComponent.class))) {
            assertNotNull(zip.getEntry("manifest.md"));
            assertNotNull(zip.getEntry("jenkins-root-configuration-files/config.xml"));
            assertEquals(2, zip.size());
        }
    }

    @Test
    public void generateBundleWith2Components() throws IOException, SAXException {
        try(ZipFile zip = downloadBundle("/generateBundle?components=" +
            SupportTestUtils.componentIdsOf(ConfigFileComponent.class, AboutUser.class))) {
            assertNotNull(zip.getEntry("manifest.md"));
            assertNotNull(zip.getEntry("jenkins-root-configuration-files/config.xml"));
            assertNotNull(zip.getEntry("user.md"));
            assertEquals(3, zip.size());
        }
    }


    /**
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     * <p>
     * If any warning is reported to j.u.l logger, treat that as a sign of failure, because
     * support-core plugin works darn hard to try to generate something in the presence of failing
     * {@link Component} implementations.
     */
    @Test
    public void takeSnapshotAndMakeSureSomethingHappens() throws Exception {
        Objects.requireNonNull(j.createSlave("agent1", "test", null).getComputer()).connect(false).get();
        Objects.requireNonNull(j.createSlave("agent2", "test", null).getComputer()).connect(false).get();

        RingBufferLogHandler checker = new RingBufferLogHandler(256);
        Logger logger = Logger.getLogger(SupportPlugin.class.getPackage().getName());

        logger.addHandler(checker);

        File zipFile = File.createTempFile("test", "zip");
        try(WebClient wc = j.createWebClient()) {
            HtmlPage p = wc.goTo(action.getContextUrl());

            HtmlForm form = p.getFormByName("bundle-contents");
            HtmlButton submit = (HtmlButton) form.getElementsByTagName("button").get(0);
            Page zip = submit.click();
            IOUtils.copy(zip.getWebResponse().getContentAsStream(), Files.newOutputStream(zipFile.toPath()));

            try(ZipFile z = new ZipFile(zipFile)) {
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
                        assertNotNull(file + " was not found in the bundle",
                            z.getEntry("nodes/master/" + file));
                    }
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

    /**
     * Check that the list of files passed in exist in the bundle.
     * 
     * @param zip the bundle {@link ZipFile}
     * @param fileNames the list of files names
     */
    private void assertBundleContains(ZipFile zip, Collection<String> fileNames) {
        for (String file : fileNames) {
            assertNotNull(file + " was not found in the bundle", zip.getEntry(file));
        }
    }

    /**
     * Check that the list of files passed in do not exist in the bundle.
     *
     * @param zip the bundle {@link ZipFile}
     * @param fileNames the list of files names
     */
    private void assertBundleNotContains(ZipFile zip, Collection<String> fileNames) {
        for (String file : fileNames) {
            assertNull(file + " was found in the bundle", zip.getEntry(file));
        }
    }
}

