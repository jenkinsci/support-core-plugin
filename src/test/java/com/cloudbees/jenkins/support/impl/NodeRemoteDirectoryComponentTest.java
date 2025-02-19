package com.cloudbees.jenkins.support.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloudbees.jenkins.support.SafeLog;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;

/**
 * Tests for the {@link NodeRemoteDirectoryComponent}
 */
public class NodeRemoteDirectoryComponentTest {

    @Rule
    public JenkinsSessionRule r = new JenkinsSessionRule();

    @Before
    public void clearLog() throws Throwable {
        SafeLog.clear();
    }

    @After
    public void showLog() throws Throwable {
        SafeLog.show();
    }

    /*
     * Test adding agent remote directory content with the defaults.
     */
    @Test
    public void addContents() throws Throwable {
        r.then(j -> {
            DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

            Map<String, String> output =
                    SupportTestUtils.invokeComponentToMap(new NodeRemoteDirectoryComponent(), agent.toComputer());

            String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
            assertTrue(output.keySet().stream().anyMatch(key -> key.matches(prefix + "/support/.*.log")));
        });
    }

    /*
     * Test adding agent remote directory content with excludes pattern(s).
     */
    @Test
    public void addContentsWithExcludes() throws Throwable {
        r.then(j -> {
            DumbSlave agent = j.createSlave("agent1", "test", null);
            agent.getComputer().connect(false).get();
            j.waitOnline(agent);

            Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                    new NodeRemoteDirectoryComponent("", "**/*.log", true, 10), agent.toComputer());

            String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
            assertFalse(output.keySet().stream().anyMatch(key -> key.matches(prefix + ".*/.*.log")));
        });
    }

    /*
     * Test adding agent remote directory content with includes pattern(s).
     */
    @Test
    public void addContentsWithIncludes() throws Throwable {
        r.then(j -> {
            DumbSlave agent = j.createSlave("agent1", "test", null);
            agent.getComputer().connect(false).get();
            j.waitOnline(agent);

            Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                    new NodeRemoteDirectoryComponent("support/*.log", "", true, 10), agent.toComputer());

            String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
            assertTrue(output.keySet().stream().anyMatch(key -> key.matches(prefix + "/support/.*.log")));
        });
    }

    /*
     * Test adding agent remote directory content with includes pattern(s).
     */
    @Test
    public void addContentsWithMaxDepth() throws Throwable {
        r.then(j -> {
            DumbSlave agent = j.createSlave("agent1", "test", null);
            agent.getComputer().connect(false).get();
            j.waitOnline(agent);

            Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                    new NodeRemoteDirectoryComponent("", "", true, 1), agent.toComputer());

            String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
            assertFalse(output.keySet().stream().anyMatch(key -> key.matches(prefix + "/support/.*.log")));
        });
    }
}
