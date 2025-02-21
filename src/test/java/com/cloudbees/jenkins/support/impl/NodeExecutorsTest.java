package com.cloudbees.jenkins.support.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for the {@link NodeExecutors}
 */
@WithJenkins
class NodeExecutorsTest {

    @Test
    void addContents(JenkinsRule j) throws Exception {
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        String executorMd = SupportTestUtils.invokeComponentToString(new NodeExecutors());
        assertNotNull(executorMd);
        String[] agentSnippets = executorMd.split(" {2}\\* ");
        // headers, built-in node and permanent agent
        assertEquals(3, agentSnippets.length);
        Scanner scanner = new Scanner(agentSnippets[1]);
        assertTrue(scanner.nextLine()
                .contains(Objects.requireNonNull(j.jenkins.toComputer()).getDisplayName()));
        assertTrue(scanner.nextLine().contains("- Executor #0"));
        assertTrue(scanner.nextLine().contains("- active: true"));
        assertTrue(scanner.nextLine().contains("- busy: false"));
        assertTrue(scanner.nextLine().contains("- causesOfInterruption: []"));
        assertTrue(scanner.nextLine().contains("- idle: true"));
        assertTrue(scanner.nextLine().contains("- idleStartMilliseconds:"));
        assertTrue(scanner.nextLine().contains("- progress: -1"));
        assertTrue(scanner.nextLine().contains("- state: NEW"));
        assertTrue(scanner.nextLine().contains("- Executor #1"));
        assertTrue(scanner.nextLine().contains("- active: true"));
        assertTrue(scanner.nextLine().contains("- busy: false"));
        assertTrue(scanner.nextLine().contains("- causesOfInterruption: []"));
        assertTrue(scanner.nextLine().contains("- idle: true"));
        assertTrue(scanner.nextLine().contains("- idleStartMilliseconds:"));
        assertTrue(scanner.nextLine().contains("- progress: -1"));
        assertTrue(scanner.nextLine().contains("- state: NEW"));
        assertFalse(scanner.hasNextLine());
        scanner = new Scanner(agentSnippets[2]);
        assertTrue(scanner.nextLine()
                .contains(Objects.requireNonNull(agent.toComputer()).getDisplayName()));
        assertTrue(scanner.nextLine().contains("- Executor #0"));
        assertTrue(scanner.nextLine().contains("- active: true"));
        assertTrue(scanner.nextLine().contains("- busy: false"));
        assertTrue(scanner.nextLine().contains("- causesOfInterruption: []"));
        assertTrue(scanner.nextLine().contains("- idle: true"));
        assertTrue(scanner.nextLine().contains("- idleStartMilliseconds:"));
        assertTrue(scanner.nextLine().contains("- progress: -1"));
        assertTrue(scanner.nextLine().contains("- state: NEW"));
        assertFalse(scanner.hasNextLine());
        scanner.close();

        // No running task
        assertFalse(executorMd.contains("- currentWorkUnit:"));
        assertFalse(executorMd.contains("- executable:"));
        assertFalse(executorMd.contains("- elapsedTime:"));
    }

    @Test
    void addContentsRunningBuild(JenkinsRule j) throws Exception {
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        WorkflowJob p = j.createProject(WorkflowJob.class, "nodeExecutorTestJob");
        p.setDefinition(new CpsFlowDefinition("node('test') {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.waitForStart("wait/1", workflowRun);

        String executorMd = SupportTestUtils.invokeComponentToMap(new NodeExecutors(), agent.toComputer())
                .get("executors.md");
        assertNotNull(executorMd);
        assertFalse(executorMd.contains(
                "  * " + Objects.requireNonNull(j.jenkins.toComputer()).getDisplayName()));
        assertTrue(executorMd.contains("  * " + agent.getDisplayName()));

        String[] agentSnippets = executorMd.split(" {2}\\* ");
        // headers and the agent
        assertEquals(2, agentSnippets.length);
        Scanner scanner = new Scanner(agentSnippets[1]);
        assertTrue(scanner.nextLine()
                .contains(Objects.requireNonNull(agent.toComputer()).getDisplayName()));
        assertTrue(scanner.nextLine().contains("- Executor #0"));
        assertTrue(scanner.nextLine().contains("- active: true"));
        assertTrue(scanner.nextLine().contains("- busy: true"));
        assertTrue(scanner.nextLine().contains("- causesOfInterruption: []"));
        assertTrue(scanner.nextLine().contains("- idle: false"));
        assertTrue(scanner.nextLine().contains("- idleStartMilliseconds:"));
        assertTrue(scanner.nextLine().contains("- progress: -1"));
        assertTrue(Pattern.compile("- state: (?!NEW).*")
                .matcher(scanner.nextLine())
                .find());
        assertTrue(Pattern.compile("- currentWorkUnit:.*" + workflowRun.getFullDisplayName() + ".*")
                .matcher(scanner.nextLine())
                .find());
        assertTrue(
                Pattern.compile("- executable:.*(" + workflowRun.getExternalizableId()
                                + /* TODO delete after https://github.com/jenkinsci/workflow-job-plugin/pull/499 */ "|"
                                + workflowRun.getFullDisplayName() + ").*")
                        .matcher(scanner.nextLine())
                        .find(),
                executorMd);
        assertTrue(scanner.nextLine().contains("- elapsedTime: "));
        assertFalse(scanner.hasNextLine());
        scanner.close();

        SemaphoreStep.success("wait/1", null);
        j.waitForCompletion(workflowRun);

        executorMd = SupportTestUtils.invokeComponentToMap(new NodeExecutors(), agent.toComputer())
                .get("executors.md");
        assertNotNull(executorMd);
        assertFalse(executorMd.contains(
                "  * " + Objects.requireNonNull(j.jenkins.toComputer()).getDisplayName()));
        assertTrue(executorMd.contains("  * " + agent.getDisplayName()));

        agentSnippets = executorMd.split(" {2}\\* ");
        // headers and the agent
        assertEquals(2, agentSnippets.length);
        scanner = new Scanner(agentSnippets[1]);
        assertTrue(scanner.nextLine()
                .contains(Objects.requireNonNull(agent.toComputer()).getDisplayName()));
        assertTrue(scanner.nextLine().contains("- Executor #0"));
        assertTrue(scanner.nextLine().contains("- active: true"));
        assertTrue(scanner.nextLine().contains("- busy: false"));
        assertTrue(scanner.nextLine().contains("- causesOfInterruption: []"));
        assertTrue(scanner.nextLine().contains("- idle: true"));
        assertTrue(scanner.nextLine().contains("- idleStartMilliseconds:"));
        assertTrue(scanner.nextLine().contains("- progress: -1"));
        assertTrue(scanner.nextLine().contains("- state: NEW"));
        assertFalse(scanner.hasNextLine());

        // No running task
        assertFalse(executorMd.contains("- currentWorkUnit:"));
        assertFalse(executorMd.contains("- executable:"));
        assertFalse(executorMd.contains("- elapsedTime:"));
    }

    @Test
    void addContentsFiltered(JenkinsRule j) throws Exception {
        ContentFilters.get().setEnabled(true);
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        WorkflowJob p = j.createProject(WorkflowJob.class, "nodeExecutorTestJob");
        p.setDefinition(new CpsFlowDefinition("node('test') {semaphore 'wait'}", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        SemaphoreStep.waitForStart("wait/1", workflowRun);

        ContentFilter filter = SupportPlugin.getDefaultContentFilter();

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

        SemaphoreStep.success("wait/1", null);
        j.waitForCompletion(workflowRun);
    }
}
