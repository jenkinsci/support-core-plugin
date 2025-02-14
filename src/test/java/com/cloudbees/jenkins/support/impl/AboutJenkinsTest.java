/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.model.Node;
import hudson.model.User;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.OfflineCause.UserCause;
import java.util.Objects;
import jenkins.model.Jenkins;
import jenkins.model.identity.IdentityRootAction;
import jenkins.slaves.RemotingVersionInfo;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Stephen Connolly
 */
public class AboutJenkinsTest {

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
    @Issue("JENKINS-56245")
    public void testAboutJenkinsContent() {

        String aboutMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));

        assertThat(
                aboutMdToString,
                containsString("  * Instance ID: `" + j.getInstance().getLegacyInstanceId()));
        IdentityRootAction idRootaction =
                j.getInstance().getExtensionList(IdentityRootAction.class).get(0);
        assertThat(aboutMdToString, containsString(idRootaction.getPublicKey()));
        assertThat(aboutMdToString, containsString(idRootaction.getFingerprint()));
        assertThat(
                aboutMdToString,
                containsString("  * Embedded Version: `"
                        + RemotingVersionInfo.getEmbeddedVersion().toString()));
        assertThat(
                aboutMdToString,
                containsString("  * Minimum Supported Version: `"
                        + RemotingVersionInfo.getMinimumSupportedVersion().toString()));
    }

    @Test
    public void testAboutNodesContent() throws Exception {

        DumbSlave tcp1 = j.createSlave("tcp1", "test", null);
        tcp1.setLauncher(new JNLPLauncher());
        tcp1.save();

        String aboutMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));
        assertThat(aboutMdToString, containsString("  * `" + tcp1.getNodeName() + "` (`hudson.slaves.DumbSlave`)"));
        assertThat(aboutMdToString, containsString("      - Launch method:  `hudson.slaves.JNLPLauncher`"));
    }

    @Test
    @Issue("JENKINS-68743")
    public void testAboutNodesContent_OfflineBuiltIn() {

        Node builtInNode = Jenkins.get();
        String aboutMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));
        assertThat(aboutMdToString, containsString("  * master (Jenkins)"));
        assertThat(aboutMdToString, containsString("      - Status:         on-line"));
        assertThat(aboutMdToString, containsString("      - Marked Offline: false"));

        Objects.requireNonNull(builtInNode.toComputer())
                .setTemporarilyOffline(true, new UserCause(User.current(), "test"));

        aboutMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));
        assertThat(aboutMdToString, containsString("  * master (Jenkins)"));
        assertThat(aboutMdToString, containsString("      - Status:         on-line"));
        assertThat(aboutMdToString, containsString("      - Marked Offline: true"));
    }
}
