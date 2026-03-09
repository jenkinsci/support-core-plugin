/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JenkinsLogsTest {

    @Test
    void testJenkinsLogsContent(JenkinsRule j) {
        String jenkinsLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(JenkinsLogs.class)));
        assertFalse(jenkinsLogs.isEmpty(), "Should write something");
    }
}
