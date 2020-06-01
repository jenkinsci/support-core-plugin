package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import jenkins.AgentProtocol;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.CoreMatchers.containsString;

public class AgentProtocolsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAgentProtocolsContents() {
        String itemsContentToString = SupportTestUtils.invokeComponentToString(
                ExtensionList.lookup(Component.class).get(AgentProtocols.class));
        AgentProtocol.all().forEach(s -> {
            MatcherAssert.assertThat(itemsContentToString,
                    containsString(" * `" + s.getName() + "`: " + s.getDisplayName()));
        });
    }
}
