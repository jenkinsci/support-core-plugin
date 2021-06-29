package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.ExtensionList;
import hudson.slaves.DumbSlave;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;

import static org.hamcrest.Matchers.hasKey;

public class SlaveLaunchLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        DumbSlave s = j.createSlave();
        Map<String, String> output = SupportTestUtils.invokeComponentToMap(ExtensionList.lookupSingleton(SlaveLaunchLogs.class));
        String key = "nodes/slave/" + s.getNodeName() + "/launchLogs/slave.log";
        MatcherAssert.assertThat(output, hasKey(key));
    }
}
