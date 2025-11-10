/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.triggers.SafeTimerTask;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Objects;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class TaskLogsTest {

    @Test
    void testTaskRootSafeTimerLogs(JenkinsRule j) throws IOException {
        File safeTimerTasksDir = SafeTimerTask.getLogsRoot();
        safeTimerTasksDir.mkdir();
        File testFile = new File(safeTimerTasksDir, "test.log");
        Files.createFile(testFile.toPath());
        Files.write(
                testFile.toPath(),
                Collections.singletonList("This is a test from SafeTimerTask dir"),
                Charset.defaultCharset());

        String otherLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(TaskLogs.class)));
        assertFalse(otherLogs.isEmpty(), "Should collect *.log under the SafeTimerTask dir");
        assertThat(otherLogs, Matchers.containsString("This is a test from SafeTimerTask dir"));
    }

    @Test
    void testTaskLogs(JenkinsRule j) throws IOException {
        File tasksDir = new File(SafeTimerTask.getLogsRoot(), "tasks");
        SafeTimerTask.getLogsRoot().mkdir();
        tasksDir.mkdir();
        File testFile = new File(tasksDir, "test.log");
        Files.createFile(testFile.toPath());
        Files.write(
                testFile.toPath(),
                Collections.singletonList("This is a test from tasks dir"),
                Charset.defaultCharset());

        String otherLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(TaskLogs.class)));
        assertFalse(otherLogs.isEmpty(), "Should collect *.log under the tasks dir");
        assertThat(otherLogs, Matchers.containsString("This is a test from tasks dir"));
    }
}
