package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import junit.framework.AssertionFailedError;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class RunDirectoryComponentTest {

    private static final String JOB_NAME = "job-name";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void addContentsFromFreestyle() throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, JOB_NAME);
        FreeStyleBuild fBuild = j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new).waitForStart());
        
        j.waitUntilNoActivity();
        
        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), fBuild);

        String prefix = "items/" + JOB_NAME + "/builds/" + fBuild.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<build>"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("Building in workspace"));
    }

    @Test
    public void addContentsFromPipeline() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);
        
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertTrue(output.containsKey(prefix + "/workflow/2.xml"));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build>"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
    }

    @Test
    public void addContentsFromWithExcludes() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("", "workflow/**, */log", true, 10), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertFalse(output.containsKey(prefix + "/workflow/2.xml"));
    }

    @Test
    public void addContentsFromWithIncludes() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("workflow/**", "", true, 10), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertFalse(output.containsKey(prefix + "/build.xml"));
        assertFalse(output.containsKey(prefix + "/log"));
        assertTrue(output.containsKey(prefix + "/workflow/2.xml"));
    }

    @Test
    public void addContentsFromWithMaxDepth() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("", "", true, 1), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertFalse(output.containsKey(prefix + "/workflow/2.xml"));
    }

    @Test
    public void addContentsFromWithIncludesExcludes() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("**/*.xml", "workflow/**", true, 10), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertFalse(output.containsKey(prefix + "/log"));
        assertFalse(output.containsKey(prefix + "/workflow/2.xml"));
    }

}