/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.logging.LogRecorder;
import hudson.security.Permission;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.triggers.SafeTimerTask;
import jenkins.model.Jenkins;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CustomLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        SupportTestUtils.invokeComponentToString(new Component() {

            @NonNull
            @Override
            public Set<Permission> getRequiredPermissions() {
                return Collections.singleton(Jenkins.ADMINISTER);
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "";
            }

            @Override
            public void addContents(@NonNull Container container) {
                Logger.getLogger(CustomLogsTest.class.getName()).fine("Testing custom log recorders");
            }
        });
        String customLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));
        assertFalse("Should write CustomLogsTest FINE logs", customLogs.isEmpty());
        assertThat(customLogs, Matchers.containsString("Testing custom log recorders"));
    }

    @Test
    public void testCustomLogRotation() throws IOException {
        LogRecorder test1LogRecorder = new LogRecorder("test1");
        j.getInstance().getLog().getRecorders().add(test1LogRecorder);

        LogRecorder test2LogRecorder = new LogRecorder("test2");
        j.getInstance().getLog().getRecorders().add(test2LogRecorder);

        // Create dummy log files
        File customLogsDir = new File(SafeTimerTask.getLogsRoot(), "custom");
        customLogsDir.mkdirs();

        // Create dummy log for test1 log recorder
        try (FileWriter writer = new FileWriter(new File(customLogsDir, "test1.log"))) {
            writer.write("test1 one");
        }
        try (FileWriter writer = new FileWriter(new File(customLogsDir, "test1.log.1"))) {
            writer.write("test1 two");
        }
        try (FileWriter writer = new FileWriter(new File(customLogsDir, "test1.log.2"))) {
            writer.write("test1 three");
        }

        // Create dummy log for test2 log recorder
        try (FileWriter writer = new FileWriter(new File(customLogsDir, "test2.log"))) {
            writer.write("test2 one");
        }
        try (FileWriter writer = new FileWriter(new File(customLogsDir, "test2.log.1"))) {
            writer.write("test2 two");
        }
        try (FileWriter writer = new FileWriter(new File(customLogsDir, "test2.log.2"))) {
            writer.write("test2 three");
        }

        // Invoke the component and get the result map
        Map<String, String> resultMap = SupportTestUtils.invokeComponentToMap(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));

        // Create the expected map
        Map<String, String> expectedMap = new TreeMap<>();
        expectedMap.put("nodes/master/logs/custom/test1.log", "test1 one");
        expectedMap.put("nodes/master/logs/custom/test1.log.1", "test1 two");
        expectedMap.put("nodes/master/logs/custom/test1.log.2", "test1 three");
        expectedMap.put("nodes/master/logs/custom/test2.log", "test2 one");
        expectedMap.put("nodes/master/logs/custom/test2.log.1", "test2 two");
        expectedMap.put("nodes/master/logs/custom/test2.log.2", "test2 three");

        // Assert the result map
        assertEquals(expectedMap, resultMap);
    }
}

