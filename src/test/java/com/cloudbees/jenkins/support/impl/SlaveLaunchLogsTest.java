package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import jenkins.slaves.StandardOutputSwapper;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class SlaveLaunchLogsTest {

    static {
        StandardOutputSwapper.disabled = true; // TODO JENKINS-65582 noisy
        // TODO also suppress JnlpSlaveRestarterInstaller
    }

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

    @Test
    public void offlineAgent() throws Exception {
        var s = j.createOnlineSlave();
        var name = s.getNodeName();
        s.toComputer().disconnect(null).get();
        assertThat(
                "still includes something",
                SupportTestUtils.invokeComponentToMap(ExtensionList.lookupSingleton(SlaveLaunchLogs.class))
                        .get("nodes/slave/" + name + "/launchLogs/slave.log"),
                allOf(notNullValue(), containsString("Remoting version: "), containsString("Connection terminated")));
    }

    @Ignore("TODO not sure how to archive the logs before deletion from SlaveComputer.kill")
    @Test
    public void deletedAgent() throws Exception {
        var s = j.createOnlineSlave();
        var name = s.getNodeName();
        s.toComputer().disconnect(null).get();
        j.jenkins.removeNode(s);
        assertThat(
                "still includes something",
                SupportTestUtils.invokeComponentToString(ExtensionList.lookupSingleton(SlaveLaunchLogs.class)),
                allOf(notNullValue(), containsString("Remoting version: "), containsString("Connection terminated")));
    }

    @Test
    public void multipleLaunchLogs() throws Exception {
        var s = j.createOnlineSlave();
        var name = s.getNodeName();
        s.toComputer().disconnect(null).get();
        Thread.sleep(1000); // TODO otherwise log is not flushed?
        s.toComputer().connect(false).get();
        assertThat(
                "notes both launch logs",
                SupportTestUtils.invokeComponentToString(ExtensionList.lookupSingleton(SlaveLaunchLogs.class)),
                allOf(
                        notNullValue(),
                        containsString("Launch attempt #1"),
                        containsString("Connection terminated"),
                        containsString("Launch attempt #2")));
    }

    @TestExtension("multipleLaunchLogs")
    public static final class DistinctiveLaunchMessage extends ComputerListener {
        int count;

        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            if (c instanceof SlaveComputer) {
                listener.getLogger().println("Launch attempt #" + ++count);
            }
        }
    }

    // TODO honor SafeTimerTask.getLogsRoot (if applicable)
    // TODO rotation of old or excessively long logs

}
