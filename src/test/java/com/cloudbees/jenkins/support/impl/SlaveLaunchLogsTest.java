package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.ExtensionList;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;

public class SlaveLaunchLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public InboundAgentRule inboundAgents = new InboundAgentRule();

    @Test
    public void onlineOutboundAgent() throws Exception {
        var s = j.createOnlineSlave();
        assertThat(
                "reflects JenkinsRule.createComputerLauncher command & SlaveComputer.setChannel",
                SupportTestUtils.invokeComponentToMap(ExtensionList.lookupSingleton(SlaveLaunchLogs.class))
                        .get("nodes/slave/" + s.getNodeName() + "/launchLogs/slave.log"),
                allOf(
                        notNullValue(),
                        containsString("-XX:+PrintCommandLineFlags"),
                        containsString("Remoting version: ")));
    }

    @Test
    public void onlineInboundAgent() throws Exception {
        inboundAgents.createAgent(j, "remote");
        assertThat(
                "reflects DefaultJnlpSlaveReceiver.beforeChannel & SlaveComputer.setChannel",
                SupportTestUtils.invokeComponentToMap(ExtensionList.lookupSingleton(SlaveLaunchLogs.class))
                        .get("nodes/slave/remote/launchLogs/slave.log"),
                allOf(
                        notNullValue(),
                        containsString("Inbound agent connected from"),
                        containsString("Communication Protocol: JNLP4-connect")));
    }

    @Test
    public void component() throws Exception {
        var s = j.createSlave();
        assertThat(
                SupportTestUtils.invokeComponentToMap(
                        ExtensionList.lookupSingleton(SlaveLaunchLogs.class), s.toComputer()),
                hasKey("nodes/slave/" + s.getNodeName() + "/launchLogs/slave.log"));
    }

    // TODO logs from offline agent
    // TODO logs from a deleted agent
    // TODO logs from multiple launch attempts of same agent
    // TODO honor SafeTimerTask.getLogsRoot (if applicable)
    // TODO rotation of old or excessively long logs

}
