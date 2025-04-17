/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.logging.LogRecorder;
import hudson.triggers.SafeTimerTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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

    @Test
    void testCustomLogRotation(JenkinsRule j) throws IOException {
        LogRecorder test1LogRecorder = new LogRecorder("test1");
        j.getInstance().getLog().getRecorders().add(test1LogRecorder);

        LogRecorder test2LogRecorder = new LogRecorder("second.test2");
        j.getInstance().getLog().getRecorders().add(test2LogRecorder);

        LogRecorder nonRotatedCustomLog = new LogRecorder("nonRotatedCustomLog");
        j.getInstance().getLog().getRecorders().add(nonRotatedCustomLog);

        // Create dummy log files
        File customLogsDir = new File(SafeTimerTask.getLogsRoot(), "custom");
        customLogsDir.mkdirs();

        // Create dummy log for test1 log recorder
        Files.writeString(Paths.get(customLogsDir.getPath(), "test1.log"), "test1 one");
        Files.writeString(Paths.get(customLogsDir.getPath(), "test1.log.1"), "test1 two");
        Files.writeString(Paths.get(customLogsDir.getPath(), "test1.log.2"), "test1 three");

        // Create dummy log for second.test2 log recorder
        Files.writeString(Paths.get(customLogsDir.getPath(), "second.test2.log"), "second.test2 one");
        Files.writeString(Paths.get(customLogsDir.getPath(), "second.test2.log.1"), "second.test2 two");
        Files.writeString(Paths.get(customLogsDir.getPath(), "second.test2.log.2"), "second.test2 three");

        // Create dummy log for nonRotatedCustomLog
        Files.writeString(Paths.get(customLogsDir.getPath(), "nonRotatedCustomLog.log"), "nonRotatedCustomLog one");

        // Invoke the component and get the result map
        Map<String, String> resultMap = SupportTestUtils.invokeComponentToMap(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));

        // Create the expected map
        Map<String, String> expectedMap = new TreeMap<>();
        expectedMap.put("nodes/master/logs/custom/test1.log", "test1 one");
        expectedMap.put("nodes/master/logs/custom/test1.log.1", "test1 two");
        expectedMap.put("nodes/master/logs/custom/test1.log.2", "test1 three");
        expectedMap.put("nodes/master/logs/custom/second.test2.log", "second.test2 one");
        expectedMap.put("nodes/master/logs/custom/second.test2.log.1", "second.test2 two");
        expectedMap.put("nodes/master/logs/custom/second.test2.log.2", "second.test2 three");
        expectedMap.put("nodes/master/logs/custom/nonRotatedCustomLog.log", "nonRotatedCustomLog one");

        // Assert the result map
        assertEquals(expectedMap, resultMap);

        // Cleanup all the dummy files
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "test1.log"));
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "test1.log.1"));
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "test1.log.2"));
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "second.test2.log"));
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "second.test2.log.1"));
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "second.test2.log.2"));
        Files.deleteIfExists(Paths.get(customLogsDir.getPath(), "nonRotatedCustomLog.log"));
    }
}
