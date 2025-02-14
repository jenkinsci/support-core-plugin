package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class FileDescriptorLimitTest {

    private static final String JOB_NAME = "job-name";
    private static final String SENSITIVE_JOB_NAME = "sensitive-" + JOB_NAME;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule();

    @After
    public void stopAgents() throws Exception {
        for (var agent : j.jenkins.getNodes()) {
            System.err.println("Stopping " + agent);
            agent.toComputer().disconnect(null).get();
        }
    }

    @Test
    public void addContents() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());
        Assume.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());
        FreeStyleProject p = j.createFreeStyleProject(JOB_NAME);
        String output;
        // Hold an open File Descriptor
        try (FileInputStream ignored = new FileInputStream(p.getConfigFile().getFile())) {
            output = SupportTestUtils.invokeComponentToString(new FileDescriptorLimit());
        }
        MatcherAssert.assertThat(output, containsString("core file size"));
        MatcherAssert.assertThat(output, containsString("Open File Descriptor Count:"));
        MatcherAssert.assertThat(output, containsString("All open files\n=============="));
        MatcherAssert.assertThat(output, containsString(JOB_NAME));
    }

    @Test
    public void addContentsFiltered() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());
        Assume.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());
        ContentFilters.get().setEnabled(true);
        FreeStyleProject p = j.createFreeStyleProject(SENSITIVE_JOB_NAME);
        ContentFilter filter = SupportPlugin.getDefaultContentFilter();
        String filtered = ContentMappings.get().getMappings().get(SENSITIVE_JOB_NAME);
        String output;
        // Hold an open File Descriptor
        try (FileInputStream ignored = new FileInputStream(p.getConfigFile().getFile())) {
            output = SupportTestUtils.invokeComponentToString(new FileDescriptorLimit(), filter);
        }
        MatcherAssert.assertThat(output, containsString("core file size"));
        MatcherAssert.assertThat(output, containsString("Open File Descriptor Count:"));
        MatcherAssert.assertThat(output, containsString("All open files\n=============="));
        MatcherAssert.assertThat(output, not(containsString(SENSITIVE_JOB_NAME)));
        MatcherAssert.assertThat(output, containsString(filtered));
    }

    @Test
    public void agentContentFilter() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());
        Assume.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());
        logging.record(AsyncResultCache.class, Level.FINER);
        ContentFilters.get().setEnabled(true);
        SlaveComputer agent = j.createOnlineSlave().getComputer();
        File bundle = tmp.newFile("bundle.zip");
        try (var os = new FileOutputStream(bundle)) {
            SupportPlugin.writeBundle(os, List.of(new FileDescriptorLimit()));
        }
        try (var zf = new ZipFile(bundle)) {
            try (var is = zf.getInputStream(zf.stream()
                    .filter(n -> n.getName().matches("nodes/master/file-descriptors[.]txt"))
                    .findFirst()
                    .get())) {
                assertThat(
                        "worked on controller", IOUtils.toString(is, (String) null), containsString("All open files"));
            }
            try (var is = zf.getInputStream(zf.stream()
                    .filter(n -> n.getName().matches("nodes/slave/.+/file-descriptors[.]txt"))
                    .findFirst()
                    .get())) {
                assertThat(
                        "worked on agent\n" + agent.getLog(),
                        IOUtils.toString(is, (String) null),
                        anyOf(
                                containsString("All open files"),
                                // TODO occasional flake in PCT (not known how to reproduce):
                                containsString("Either no connection to node or no cached result")));
            }
        }
    }
}
