package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMapping;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class RunningBuildsTest {

    private static final String JOB_NAME = "job-name";
    private static final String SENSITIVE_WORD = "authenticated";
    private static final String SENSITIVE_JOB_NAME = SENSITIVE_WORD + "-" + JOB_NAME;
    private static final String FILTERED_SENSITIVE_WORD = "filtered";
    private static final String FILTERED_JOB_NAME = FILTERED_SENSITIVE_WORD + "-" + JOB_NAME;
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
    public void addContentsFiltered() throws Exception {
        ContentFilters.get().setEnabled(true);
        ContentMapping mapping = ContentMapping.of(SENSITIVE_WORD, FILTERED_SENSITIVE_WORD);
        ContentMappings.get().getMappingOrCreate(mapping.getOriginal(), original -> mapping);
        ContentFilter filter = SupportPlugin.getContentFilter().orElseThrow(AssertionFailedError::new);
        FreeStyleProject p = j.createFreeStyleProject(SENSITIVE_JOB_NAME);
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild build = freeStyleBuildQueueTaskFuture.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningBuilds(), filter);

        assertThat(output, not(containsString(String.format(EXPECTED_OUTPUT_FORMAT, p.getName(), build.getNumber()))));
        assertThat(output, containsString(String.format(EXPECTED_OUTPUT_FORMAT, FILTERED_JOB_NAME, build.getNumber())));
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
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        Optional<QueueTaskFuture<WorkflowRun>> optionalWorkflowRunQueueTaskFuture = Optional.ofNullable(p.scheduleBuild2(0));
        QueueTaskFuture<WorkflowRun> workflowRunQueueTaskFuture = optionalWorkflowRunQueueTaskFuture
            .orElseThrow(AssertionFailedError::new);
        WorkflowRun workflowRun = workflowRunQueueTaskFuture.waitForStart();
        SemaphoreStep.waitForStart("wait/1", workflowRun);

        String output = SupportTestUtils.invokeComponentToString(new RunningBuilds());

        assertThat(output, containsString(String.format(EXPECTED_OUTPUT_FORMAT, p.getName(), workflowRun.getNumber())));
    }

}