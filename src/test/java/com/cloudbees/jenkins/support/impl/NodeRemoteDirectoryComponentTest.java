package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for the {@link NodeRemoteDirectoryComponent}
 */
public class NodeRemoteDirectoryComponentTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /*
     * Test adding agent remote directory content with the defaults.
     */
    @Test
    public void addContents() throws Exception {
        DumbSlave agent = j.createOnlineSlave(Label.parseExpression("test"), null);

        Map<String, String> output =
                SupportTestUtils.invokeComponentToMap(new NodeRemoteDirectoryComponent(), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertThat(output.keySet(), hasItem(matchesPattern(prefix + "/support/.*.log")));
    }

    /*
     * Test adding agent remote directory content with excludes pattern(s).
     */
    @Test
    public void addContentsWithExcludes() throws Exception {
        DumbSlave agent = j.createSlave("agent1", "test", null);
        agent.getComputer().connect(false).get();
        j.waitOnline(agent);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new NodeRemoteDirectoryComponent("", "**/*.log", true, 10), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertThat(output.keySet(), not(hasItem(matchesPattern(prefix + ".*/.*.log"))));
    }

    /*
     * Test adding agent remote directory content with includes pattern(s).
     */
    @Test
    public void addContentsWithIncludes() throws Exception {
        DumbSlave agent = j.createSlave("agent1", "test", null);
        agent.getComputer().connect(false).get();
        j.waitOnline(agent);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new NodeRemoteDirectoryComponent("support/*.log", "", true, 10), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertThat(output.keySet(), hasItem(matchesPattern(prefix + "/support/.*.log")));
    }

    /*
     * Test adding agent remote directory content with includes pattern(s).
     */
    @Test
    public void addContentsWithMaxDepth() throws Exception {
        DumbSlave agent = j.createSlave("agent1", "test", null);
        agent.getComputer().connect(false).get();
        j.waitOnline(agent);

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                new NodeRemoteDirectoryComponent("", "", true, 1), agent.toComputer());

        String prefix = "nodes/slave/" + agent.getNodeName() + "/remote";
        assertThat(output.keySet(), not(hasItem(matchesPattern(prefix + "/support/.*.log"))));
    }
}
