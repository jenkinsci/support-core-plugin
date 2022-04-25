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

public class FileDescriptorLimitTest {

    private static final String JOB_NAME = "job-name";
    private static final String SENSITIVE_WORD = "sensitive";
    private static final String SENSITIVE_JOB_NAME = SENSITIVE_WORD + "-" + JOB_NAME;
    private static final String FILTERED_SENSITIVE_WORD = "filtered";
    private static final String FILTERED_JOB_NAME = FILTERED_SENSITIVE_WORD + "-" + JOB_NAME;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void addContents() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());
        Assume.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());
        FreeStyleProject p = j.createFreeStyleProject(SENSITIVE_JOB_NAME);
        String output;
        // Hold an open File Descriptor
        try (FileInputStream ignored = new FileInputStream(p.getConfigFile().getFile())) {
            output = SupportTestUtils.invokeComponentToString(new FileDescriptorLimit());
        }
        MatcherAssert.assertThat(output, containsString("core file size"));
        MatcherAssert.assertThat(output, containsString("Open File Descriptor Count:"));
        MatcherAssert.assertThat(output, containsString("All open files\n=============="));
        MatcherAssert.assertThat(output, not(containsString(FILTERED_JOB_NAME)));
        MatcherAssert.assertThat(output, containsString(SENSITIVE_JOB_NAME));
    }

    @Ignore("TODO flaky test")
    @Test
    public void addContentsFiltered() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());
        Assume.assumeTrue(SystemPlatform.LINUX == SystemPlatform.current());
        ContentFilters.get().setEnabled(true);
        ContentMapping mapping = ContentMapping.of(SENSITIVE_WORD, FILTERED_SENSITIVE_WORD);
        ContentMappings.get().getMappingOrCreate(mapping.getOriginal(), original -> mapping);
        ContentFilter filter = SupportPlugin.getContentFilter().orElseThrow(AssertionFailedError::new);
        FreeStyleProject p = j.createFreeStyleProject(SENSITIVE_JOB_NAME);
        String output;
        // Hold an open File Descriptor
        try (FileInputStream ignored = new FileInputStream(p.getConfigFile().getFile())) {
             output = SupportTestUtils.invokeComponentToString(new FileDescriptorLimit(), filter);
        }
        MatcherAssert.assertThat(output, containsString("core file size"));
        MatcherAssert.assertThat(output, containsString("Open File Descriptor Count:"));
        MatcherAssert.assertThat(output, containsString("All open files\n=============="));
        MatcherAssert.assertThat(output, not(containsString(SENSITIVE_JOB_NAME)));
        MatcherAssert.assertThat(output, containsString(FILTERED_JOB_NAME));
    }
}
