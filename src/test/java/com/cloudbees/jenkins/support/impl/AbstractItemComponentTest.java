package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
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
import org.jvnet.hudson.test.MockFolder;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class AbstractItemComponentTest {

    private static final String JOB_NAME = "job-name";
    private static final String FOLDER_NAME = "folder-name";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test adding item directory content with defaults for a folder.
     */
    @Test
    public void addContentsFromFolder() throws Exception {

        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new).waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent(), f);

        String prefix = "items/" + FOLDER_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/build.xml"));
        assertThat(output.get(prefix + "/config.xml"), Matchers.containsString("<org.jvnet.hudson.test.MockFolder>"));
    }

    /**
     * Test adding item directory content with includes patterns for a folder.
     */
    @Test
    public void addContentsFromFolderWithIncludes() throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new).waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("**/config.xml, **/jobs/*.xml", "", true, 10), f);

        String prefix = "items/" + FOLDER_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/nextBuildNumber"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/log"));
    }

    /**
     * Test adding item directory content with excludes patterns for a folder.
     */
    @Test
    public void addContentsFromFolderWithExcludes() throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new).waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("", "**/builds/**", true, 10), f);

        String prefix = "items/" + FOLDER_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/nextBuildNumber"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/log"));
    }

    /**
     * Test adding item directory content with excludes patterns for a freestyle job.
     */
    @Test
    public void addContentsFromFolderWithIncludesExcludes() throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new).waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("**/*.xml", "**/builds/**", true, 10), f);

        String prefix = "items/" + FOLDER_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/config.xml"));
        assertTrue(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/nextBuildNumber"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/log"));
    }

    /**
     * Test adding item directory content with defaults for a freestyle job.
     */
    @Test
    public void addContentsFromFreestyle() throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        FreeStyleProject p = f.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new).waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent(), p);

        String prefix = "items/" + FOLDER_NAME + "/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        // Default max depth is 2
        assertFalse(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertThat(output.get(prefix + "/config.xml"), Matchers.containsString("<project>"));
    }

    /**
     * Test adding item directory content with defaults for a pipeline.
     */
    @Test
    public void addContentsFromPipeline() throws Exception {
        MockFolder folder = j.createFolder(FOLDER_NAME);
        WorkflowJob p = folder.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent(), p);

        String prefix = "items/" + FOLDER_NAME + "/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/nextBuildNumber"));
        // Default max depth prevent the following
        assertFalse(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertFalse(output.containsKey(prefix + "/builds/1/workflow/2.xml"));
        assertThat(output.get(prefix + "/config.xml"), Matchers.containsString("<flow-definition>"));
        assertThat(output.get(prefix + "/nextBuildNumber"), Matchers.containsString("2"));
    }

    /**
     * Test adding item directory content with excludes patterns.
     */
    @Test
    public void addContentsFromJobWithExcludes() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        AbstractItemDirectoryComponent aiDirectoryComponent = new AbstractItemDirectoryComponent();
        aiDirectoryComponent.setExcludes("**/builds/**");
        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("", "**/builds/**", true, 10), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertFalse(output.containsKey(prefix + "/builds/1/workflow/2.xml"));
    }

    /**
     * Test adding item directory content with includes patterns.
     */
    @Test
    public void addContentsFromJobWithIncludes() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("**/config.xml, **/build.xml", "", true, 10), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertFalse(output.containsKey(prefix + "/builds/1/workflow/2.xml"));
    }

    /**
     * Test adding item directory content with includes patterns.
     */
    @Test
    public void addContentsFromJobWithMaxDepth() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("", "", true, 3), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/log"));
        assertFalse(output.containsKey(prefix + "/builds/1/workflow/2.xml"));
    }

    /**
     * Test adding item directory content with includes / excludes patterns.
     */
    @Test
    public void addContentsFromJobWithIncludesExcludes() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("**/*.xml", "**/workflow/**", true, 10), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertFalse(output.containsKey(prefix + "/builds/1/workflow/2.xml"));
    }
}