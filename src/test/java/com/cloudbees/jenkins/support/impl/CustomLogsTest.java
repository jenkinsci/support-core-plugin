/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.logging.LogRecorder;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CustomLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @After
    public void closeAll() {
        CustomLogs.closeAll();
    }

    @Test
    public void testCustomLogsContentEmpty() {
        String customLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));
        assertTrue("Should not write anything", customLogs.isEmpty());
    }

    @Test
    public void testCustomLogsContent() throws IOException {
        LogRecorder testLogRecorder = new LogRecorder("test");
        LogRecorder.Target testTarget = new LogRecorder.Target(CustomLogsTest.class.getName(), Level.FINER);
        testLogRecorder.getLoggers().add(testTarget);
        j.getInstance().getLog().getRecorders().add(testLogRecorder);
        testTarget.enable();
        testLogRecorder.save();
        Logger.getLogger(CustomLogsTest.class.getName()).fine("Testing custom log recorders");
        String customLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));
        assertFalse("Should write CustomLogsTest FINE logs", customLogs.isEmpty());
        assertThat(customLogs, Matchers.containsString("Testing custom log recorders"));
    }
}
