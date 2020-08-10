/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Objects;

import static org.junit.Assert.assertFalse;

public class JenkinsLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testJenkinsLogsContent() {
        String jenkinsLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(JenkinsLogs.class)));
        assertFalse("Should write something", jenkinsLogs.isEmpty());
    }
}
