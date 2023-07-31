package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;

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

        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new NodeExecutors(), agent.toComputer());

        assertTrue(output.keySet().stream().anyMatch(key -> key.matches("executors.md")));
    }
}
