package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class RunningBuildsTest {

    private static final String JOB_NAME = "job-name";
    private static final String FOLDER_NAME = "folder-name";
    private static final String EXPECTED_OUTPUT_FORMAT = "%s #%d";
    private static final String EXPECTED_FOLDER_OUTPUT_FORMAT = "%s/" + EXPECTED_OUTPUT_FORMAT;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void addContents() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject(JOB_NAME);
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild build = freeStyleBuildQueueTaskFuture.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningBuilds());

        assertThat(output, containsString(String.format(EXPECTED_OUTPUT_FORMAT, p.getName(), build.getNumber())));
    }

    @Test
    public void addContentsInFolder() throws Exception {
        MockFolder folder = j.createFolder(FOLDER_NAME);
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, JOB_NAME);
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild build = freeStyleBuildQueueTaskFuture.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningBuilds());

        assertThat(output, containsString(String.format(EXPECTED_FOLDER_OUTPUT_FORMAT, folder.getName(), p.getName(), build.getNumber())));
    }

    @Test
    public void addContentsPipeline() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, JOB_NAME);
        Optional<QueueTaskFuture<WorkflowRun>> optionalWorkflowRunQueueTaskFuture = Optional.ofNullable(p.scheduleBuild2(0));
        QueueTaskFuture<WorkflowRun> workflowRunQueueTaskFuture = optionalWorkflowRunQueueTaskFuture
            .orElseThrow(AssertionFailedError::new);
        WorkflowRun workflowRun = workflowRunQueueTaskFuture.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningBuilds());

        assertThat(output, containsString(String.format(EXPECTED_OUTPUT_FORMAT, p.getName(), workflowRun.getNumber())));
    }

}