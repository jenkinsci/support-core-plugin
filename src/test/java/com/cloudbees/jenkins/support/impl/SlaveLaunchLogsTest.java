package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.Matchers.hasKey;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.ExtensionList;
import hudson.slaves.DumbSlave;
import java.util.Map;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class SlaveLaunchLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void smokesRoot() throws Exception {
        DumbSlave s = j.createSlave();
        Map<String, String> output =
                SupportTestUtils.invokeComponentToMap(ExtensionList.lookupSingleton(SlaveLaunchLogs.class));
        String key = "nodes/slave/" + s.getNodeName() + "/launchLogs/slave.log";
        MatcherAssert.assertThat(output, hasKey(key));
    }

    @Test
    public void smokesComputer() throws Exception {
        DumbSlave s = j.createSlave();
        Map<String, String> output = SupportTestUtils.invokeComponentToMap(
                ExtensionList.lookupSingleton(SlaveLaunchLogs.class), s.toComputer());
        String key = "nodes/slave/" + s.getNodeName() + "/launchLogs/slave.log";
        MatcherAssert.assertThat(output, hasKey(key));
    }
}
