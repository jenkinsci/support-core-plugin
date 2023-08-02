package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link NodeExecutors}
 */
public class NodeExecutorsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void addContents() throws Exception {
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        String executorMd = SupportTestUtils.invokeComponentToString(new NodeExecutors());
        assertNotNull(executorMd);
        assertTrue(Pattern.compile(
            " {2}\\* " + Objects.requireNonNull(j.jenkins.toComputer()).getDisplayName()+ "\n" +
            " {6}- Executor #0\n" +
            " {10}- active: true\n" +
            " {10}- busy: false\n" +
            " {10}- causesOfInterruption: \\[]\n" +
            " {10}- idle: true\n" +
            " {10}- idleStartMilliseconds: [0-9]* \\(.*\\)\n" +
            " {10}- progress: -1\n" +
            " {10}- state: NEW\n" +
            " {6}- Executor #1\n" +
            " {10}- active: true\n" +
            " {10}- busy: false\n" +
            " {10}- causesOfInterruption: \\[]\n" +
            " {10}- idle: true\n" +
            " {10}- idleStartMilliseconds: [0-9]* \\(.*\\)\n" +
            " {10}- progress: -1\n" +
            " {10}- state: NEW\n").matcher(executorMd).find());
        assertTrue(Pattern.compile(" {2}\\* " + Objects.requireNonNull(agent.toComputer()).getDisplayName()+ "\n" +
            " {6}- Executor #0\n" +
            " {10}- active: true\n" +
            " {10}- busy: false\n" +
            " {10}- causesOfInterruption: \\[]\n" +
            " {10}- idle: true\n" +
            " {10}- idleStartMilliseconds: [0-9]* \\(.*\\)\n" +
            " {10}- progress: -1\n" +
            " {10}- state: NEW\n").matcher(executorMd).find());

        // No running task
        assertFalse(executorMd.contains("- currentWorkUnit:"));
        assertFalse(executorMd.contains("- executable:"));
        assertFalse(executorMd.contains("- elapsedTime:"));
    }

    @Test
    public void addContentsRunningBuild() throws Exception {
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        WorkflowJob p = j.createProject(WorkflowJob.class, "nodeExecutorTestJob");
        p.setDefinition(new CpsFlowDefinition("node('test') {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
            .orElseThrow(AssertionFailedError::new)
            .waitForStart();
        j.waitForMessage("Running on", workflowRun);

        String executorMd = SupportTestUtils.invokeComponentToMap(new NodeExecutors(), agent.toComputer()).get("executors.md");
        assertNotNull(executorMd);
        assertFalse(executorMd.contains("  * " + Objects.requireNonNull(j.jenkins.toComputer()).getDisplayName()));
        assertTrue(executorMd.contains("  * " + agent.getDisplayName()));
        assertTrue(Pattern.compile(
            " {2}\\* slave0\n" +
                " {6}- Executor #0\n" +
                " {10}- active: true\n" +
                " {10}- busy: true\n" +
                " {10}- causesOfInterruption: \\[]\n" +
                " {10}- idle: false\n" +
                " {10}- idleStartMilliseconds: [0-9]* \\(.*\\)\n" +
                " {10}- progress: -1\n" +
                " {10}- state: (?!NEW).*\n" +
                " {10}- currentWorkUnit:.*" + workflowRun.getFullDisplayName() + ".*\n" +
                " {10}- executable:.*" + workflowRun.getFullDisplayName() + ".*\n" +
                " {10}- elapsedTime:.*").matcher(executorMd).find());

        SemaphoreStep.success("wait/1", null);
        j.waitForCompletion(workflowRun);

        executorMd = SupportTestUtils.invokeComponentToMap(new NodeExecutors(), agent.toComputer()).get("executors.md");
        assertNotNull(executorMd);
        assertFalse(executorMd.contains("  * " + Objects.requireNonNull(j.jenkins.toComputer()).getDisplayName()));
        assertTrue(executorMd.contains("  * " + agent.getDisplayName()));
        assertTrue(Pattern.compile(
            " {2}\\* slave0\n" +
            " {6}- Executor #0\n" +
            " {10}- active: true\n" +
            " {10}- busy: false\n" +
            " {10}- causesOfInterruption: \\[]\n" +
            " {10}- idle: true\n" +
            " {10}- idleStartMilliseconds: [0-9]* \\(.*\\)\n" +
            " {10}- progress: -1\n" +
            " {10}- state: NEW\n").matcher(executorMd).find());

        // No running task
        assertFalse(executorMd.contains("- currentWorkUnit:"));
        assertFalse(executorMd.contains("- executable:"));
        assertFalse(executorMd.contains("- elapsedTime:"));
    }

    @Test
    public void addContentsFiltered() throws Exception {
        ContentFilters.get().setEnabled(true);
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        WorkflowJob p = j.createProject(WorkflowJob.class, "nodeExecutorTestJob");
        p.setDefinition(new CpsFlowDefinition("node('test') {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
            .orElseThrow(AssertionFailedError::new)
            .waitForStart();
        j.waitForMessage("Running on", workflowRun);

        ContentFilter filter = SupportPlugin.getContentFilter().orElseThrow(AssertionFailedError::new);

        String filteredNodeName = ContentMappings.get().getMappings().get(agent.getNodeName());
        String filteredNodeDisplayName = ContentMappings.get().getMappings().get(agent.getDisplayName());

        String filteredJobName = ContentMappings.get().getMappings().get(p.getName());
        String filteredJobDisplayName = ContentMappings.get().getMappings().get(p.getDisplayName());

        String executorMd = SupportTestUtils.invokeComponentToString(new NodeExecutors(), filter);
        assertNotNull(executorMd);
        assertFalse(executorMd.contains(p.getName()));
        assertFalse(executorMd.contains(p.getDisplayName()));
        assertFalse(executorMd.contains(agent.getNodeName()));
        assertFalse(executorMd.contains(agent.getDisplayName()));
        assertTrue(executorMd.contains(filteredNodeName));
        assertTrue(executorMd.contains(filteredNodeDisplayName));
        assertTrue(executorMd.contains(filteredJobName));
        assertTrue(executorMd.contains(filteredJobDisplayName));
    }
}
