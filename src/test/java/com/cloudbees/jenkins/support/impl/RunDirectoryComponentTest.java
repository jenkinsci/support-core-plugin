package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class RunDirectoryComponentTest {

    private static final String JOB_NAME = "job-name";

    @Test
    void addContentsFromFreestyle(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.jenkins.createProject(FreeStyleProject.class, JOB_NAME);
        FreeStyleBuild fBuild = j.waitForCompletion(Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart());

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), fBuild);

        String prefix = "items/" + JOB_NAME + "/builds/" + fBuild.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<build>"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("Building in workspace"));
    }

    @Test
    void addContentsFromPipeline(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(
                new CpsFlowDefinition("node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
        assertThat(output.keySet(), not(hasItem(Matchers.containsString("test.txt"))));
    }

    @Test
    void addContentsFromWithExcludes(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("", "workflow*/**, */log", true, 10), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/workflow"))));
    }

    @Test
    void addContentsFromWithIncludes(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("workflow*/**", "", true, 10), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertFalse(output.containsKey(prefix + "/build.xml"));
        assertFalse(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
    }

    @Test
    void addContentsFromWithMaxDepth(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output =
                SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent("", "", true, 1), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/workflow"))));
    }

    @Test
    void addContentsFromWithIncludesExcludes(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new RunDirectoryComponent("**/*.xml", "workflow*/**", true, 10), workflowRun);

        String prefix = "items/" + JOB_NAME + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertFalse(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), not(hasItem(startsWith(prefix + "/workflow"))));
    }

    @Test
    void addContentsFolder(JenkinsRule j) throws Exception {
        Folder folder = j.createProject(Folder.class, "topFolder");
        Folder subFolder = folder.createProject(Folder.class, "subFolder");
        WorkflowJob p = subFolder.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(
                new CpsFlowDefinition("node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

        String prefix = "items/" + folder.getName() + "/jobs/" + subFolder.getName() + "/jobs/" + JOB_NAME + "/builds/"
                + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
        assertThat(output.keySet(), not(hasItem(Matchers.containsString("test.txt"))));
    }

    @Test
    void addContentsCustomBuildsDir(JenkinsRule j) throws Exception {
        j.jenkins.setRawBuildsDir("${JENKINS_HOME}/buildsRoot/${ITEM_FULL_NAME}/builds");

        Folder folder = j.createProject(Folder.class, "topFolder");
        Folder subFolder = folder.createProject(Folder.class, "subFolder");
        WorkflowJob p = subFolder.createProject(WorkflowJob.class, JOB_NAME);
        p.setDefinition(
                new CpsFlowDefinition("node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);
        j.waitUntilNoActivity();

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

        String prefix = "items/" + p.getFullName() + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
        assertThat(output.keySet(), not(hasItem(Matchers.containsString("test.txt"))));

        j.jenkins.setRawBuildsDir("${ITEM_ROOTDIR}/runs");

        folder = j.createProject(Folder.class, "topFolder2");
        subFolder = folder.createProject(Folder.class, "subFolder2");
        p = subFolder.createProject(WorkflowJob.class, "testWorkflow2");
        p.setDefinition(
                new CpsFlowDefinition("node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
        workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);
        j.waitUntilNoActivity();

        output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

        prefix = "items/" + p.getFullName() + "/builds/" + workflowRun.number;
        assertTrue(output.containsKey(prefix + "/build.xml"));
        assertTrue(output.containsKey(prefix + "/log"));
        assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
        assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build"));
        assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
        assertThat(output.keySet(), not(hasItem(Matchers.containsString("test.txt"))));
    }
}
