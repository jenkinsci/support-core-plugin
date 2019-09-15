package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class RunningJobsTest {

    private static final String JOB_NAME = "job-name";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void addContents() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject(JOB_NAME);
        QueueTaskFuture<FreeStyleBuild> build = p.scheduleBuild2(0);
        build.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningJobs());

        assertThat(output, containsString(p.getName()));
    }

    @Test
    public void addContentsPipeline() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, JOB_NAME);
        QueueTaskFuture<WorkflowRun> build = p.scheduleBuild2(0);
        build.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningJobs());

        assertThat(output, containsString(p.getName()));
    }

}