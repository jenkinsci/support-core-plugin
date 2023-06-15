package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMapping;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import hudson.Functions;
import hudson.model.FreeStyleProject;
import junit.framework.AssertionFailedError;
import org.hamcrest.MatcherAssert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.FileInputStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class FileDescriptorLimitTest {

    private static final String JOB_NAME = "job-name";
    private static final String SENSITIVE_JOB_NAME = "sensitive-" + JOB_NAME;

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        ContentFilter filter = SupportPlugin.getContentFilter().orElseThrow(AssertionFailedError::new);
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
}
