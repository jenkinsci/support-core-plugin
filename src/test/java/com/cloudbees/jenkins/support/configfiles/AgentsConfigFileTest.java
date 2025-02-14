/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.support.configfiles;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import hudson.EnvVars;
import java.util.Map;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class AgentsConfigFileTest {

    private static final String SENSITIVE_AGENT_NAME = "sensitive-agent";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @After
    public void stopAgents() throws Exception {
        for (var agent : j.jenkins.getNodes()) {
            System.err.println("Stopping " + agent);
            agent.toComputer().disconnect(null).get();
        }
    }

    @Test
    public void agentsConfigFile() throws Exception {
        j.createSlave("node1", "node1", new EnvVars());
        String fileContent = SupportTestUtils.invokeComponentToString(new AgentsConfigFile());
        assertTrue(fileContent.contains("<name>node1</name>"));
    }

    @Issue("JENKINS-66064")
    @Test
    public void agentsConfigFileFiltered() throws Exception {
        ContentFilters.get().setEnabled(true);
        j.createSlave(SENSITIVE_AGENT_NAME, "node1", new EnvVars());
        Map<String, String> output = SupportTestUtils.invokeComponentToMap(new AgentsConfigFile());
        String filtered = ContentMappings.get().getMappings().get(SENSITIVE_AGENT_NAME);
        MatcherAssert.assertThat(output, hasKey("nodes/slave/" + filtered + "/config.xml"));
        MatcherAssert.assertThat(output, not(hasKey("nodes/slave/" + SENSITIVE_AGENT_NAME + "/config.xml")));
    }
}
