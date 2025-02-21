package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleProject;
import java.util.Map;
import java.util.Optional;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AbstractItemDirectoryComponentTest {

    private static final String JOB_NAME = "job-name";
    private static final String FOLDER_NAME = "folder-name";

    /*
     * Test adding item directory content with defaults for a folder.
     */
    @Test
    void addContentsFromFolder(JenkinsRule j) throws Exception {

        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent(), f);

        String prefix = "items/" + FOLDER_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/config.xml"));
        assertFalse(output.containsKey(prefix + "/jobs/subFolder/jobs/" + JOB_NAME + "/1/build.xml"));
        assertThat(output.get(prefix + "/config.xml"), containsString("<org.jvnet.hudson.test.MockFolder>"));
    }

    /*
     * Test adding item directory content with includes patterns for a folder.
     */
    @Test
    void addContentsFromFolderWithIncludes(JenkinsRule j) throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart());
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

    /*
     * Test adding item directory content with excludes patterns for a folder.
     */
    @Test
    void addContentsFromFolderWithExcludes(JenkinsRule j) throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart());
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

    /*
     * Test adding item directory content with excludes patterns for a freestyle job.
     */
    @Test
    void addContentsFromFolderWithIncludesExcludes(JenkinsRule j) throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        MockFolder subFolder = f.createProject(MockFolder.class, "subFolder");
        FreeStyleProject p = subFolder.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart());
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

    /*
     * Test adding item directory content with defaults for a freestyle job.
     */
    @Test
    void addContentsFromFreestyle(JenkinsRule j) throws Exception {
        MockFolder f = j.createFolder(FOLDER_NAME);
        FreeStyleProject p = f.createProject(FreeStyleProject.class, JOB_NAME);
        j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart());
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent(), p);

        String prefix = "items/" + FOLDER_NAME + "/jobs/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/log"));
        assertThat(output.get(prefix + "/config.xml"), containsString("<project>"));
    }

    /*
     * Test adding item directory content with defaults for a pipeline.
     */
    @Test
    void addContentsFromPipeline(JenkinsRule j) throws Exception {
        MockFolder folder = j.createFolder(FOLDER_NAME);
        WorkflowJob p = folder.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(
                new CpsFlowDefinition("node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent(), p);

        String prefix = "items/" + FOLDER_NAME + "/jobs/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/nextBuildNumber"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/log"));
        assertThat(output.keySet(), hasItem(startsWith(prefix + "/builds/1/workflow")));
        assertThat(output.get(prefix + "/config.xml"), containsString("<flow-definition"));
        assertThat(output.get(prefix + "/nextBuildNumber"), containsString("2"));
        assertThat(output.keySet(), not(hasItem(containsString("test.txt"))));
    }

    /*
     * Test adding item directory content with excludes patterns.
     */
    @Test
    void addContentsFromJobWithExcludes(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
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
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/builds/1/workflow"))));
    }

    /*
     * Test adding item directory content with includes patterns.
     */
    @Test
    void addContentsFromJobWithIncludes(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("**/config.xml, **/build.xml", "", true, 10), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/builds/1/workflow"))));
    }

    /*
     * Test adding item directory content with includes patterns.
     */
    @Test
    void addContentsFromJobWithMaxDepth(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output =
                SupportTestUtils.invokeComponentToMap(new AbstractItemDirectoryComponent("", "", true, 3), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/log"));
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/builds/1/workflow"))));
    }

    /*
     * Test adding item directory content with includes / excludes patterns.
     */
    @Test
    void addContentsFromJobWithIncludesExcludes(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new AbstractItemDirectoryComponent("**/*.xml", "**/workflow*/**", true, 10), p);

        String prefix = "items/" + JOB_NAME;
        assertTrue(output.containsKey(prefix + "/config.xml"));
        assertTrue(output.containsKey(prefix + "/builds/1/build.xml"));
        assertFalse(output.containsKey(prefix + "/builds/1/log"));
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/builds/1/workflow"))));
    }
}
