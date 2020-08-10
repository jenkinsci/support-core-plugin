/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Objects;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class OtherLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testOtherLogsContentEmpty() {
        String otherLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(OtherLogs.class)));
        assertTrue("Should not write anything", otherLogs.isEmpty());
    }

    @Test
    public void testOtherLogsRootDir() throws IOException {
        File testFile = new File(j.getInstance().getRootDir(), "test.log");
        Files.createFile(testFile.toPath());
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"), 
                Charset.defaultCharset());
        
        String otherLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(OtherLogs.class)));
        assertFalse("Should collect *.log under the root dir", otherLogs.isEmpty());
        assertThat(otherLogs , Matchers.containsString("This is a test from root dir"));
    }
}
