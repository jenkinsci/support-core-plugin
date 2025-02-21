/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.logging.LogRecorder;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class CustomLogsTest {

    @AfterEach
    public void closeAll() {
        CustomLogs.closeAll();
    }

    @Test
    void testCustomLogsContentEmpty(JenkinsRule j) {
        String customLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));
        assertTrue(customLogs.isEmpty(), "Should not write anything");
    }

    @Test
    void testCustomLogsContent(JenkinsRule j) throws IOException {
        LogRecorder testLogRecorder = new LogRecorder("test");
        LogRecorder.Target testTarget = new LogRecorder.Target(CustomLogsTest.class.getName(), Level.FINER);
        testLogRecorder.getLoggers().add(testTarget);
        j.getInstance().getLog().getRecorders().add(testLogRecorder);
        testTarget.enable();
        testLogRecorder.save();
        Logger.getLogger(CustomLogsTest.class.getName()).fine("Testing custom log recorders");
        String customLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));
        assertFalse(customLogs.isEmpty(), "Should write CustomLogsTest FINE logs");
        assertThat(customLogs, Matchers.containsString("Testing custom log recorders"));
    }
}
