package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@link NodeRemoteDirectoryComponent}
 */
public class NodeRemoteDirectoryComponentTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test adding agent remote directory content with the defaults.
     */
    @Test
    public void addContents() throws Exception {
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new NodeRemoteDirectoryComponent(), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertTrue(output.keySet().stream().anyMatch(key -> key.matches(prefix + "/support/.*.log")));
    }

    /**
     * Test adding agent remote directory content with excludes pattern(s).
     */
    @Test
    public void addContentsWithExcludes() throws Exception {
        DumbSlave agent = j.createSlave("slave1", "test", null);
        agent.getComputer().connect(false).get();
        j.waitOnline(agent);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new NodeRemoteDirectoryComponent("", "**/*.log", true, 10), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertFalse(output.keySet().stream().anyMatch(key -> key.matches(prefix + ".*/.*.log")));
    }

    /**
     * Test adding agent remote directory content with includes pattern(s).
     */
    @Test
    public void addContentsWithIncludes() throws Exception {
        DumbSlave agent = j.createSlave("slave1", "test", null);
        agent.getComputer().connect(false).get();
        j.waitOnline(agent);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new NodeRemoteDirectoryComponent("support/*.log", "", true, 10), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertTrue(output.keySet().stream().anyMatch(key -> key.matches(prefix + "/support/.*.log")));
    }

    /**
     * Test adding agent remote directory content with includes pattern(s).
     */
    @Test
    public void addContentsWithMaxDepth() throws Exception {
        DumbSlave agent = j.createSlave("slave1", "test", null);
        agent.getComputer().connect(false).get();
        j.waitOnline(agent);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new NodeRemoteDirectoryComponent("", "", true, 1), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertFalse(output.keySet().stream().anyMatch(key -> key.matches(prefix + "/support/.*.log")));
    }

}