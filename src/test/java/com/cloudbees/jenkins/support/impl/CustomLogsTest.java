/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.logging.LogRecorder;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
    public void testCustomLogsContent() throws InterruptedException, IOException {
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
        String customLogs = SupportTestUtils.invokeComponentToString(Objects.requireNonNull(ExtensionList.lookup(Component.class).get(CustomLogs.class)));
        assertFalse("Should write CustomLogsTest FINE logs", customLogs.isEmpty());
        assertThat(customLogs , Matchers.containsString("Testing custom log recorders"));
    }
}
