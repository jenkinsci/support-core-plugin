package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class RunningJobsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void addContents() throws Exception {
        String jobName = "running-job";
        FreeStyleProject p = j.createFreeStyleProject(jobName);
        QueueTaskFuture<FreeStyleBuild> build = p.scheduleBuild2(0);
        build.waitForStart();

        String output = SupportTestUtils.invokeComponentToString(new RunningJobs());

        assertThat(output, containsString(p.getUrl()));
    }
}