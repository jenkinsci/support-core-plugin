/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import jenkins.model.identity.IdentityRootAction;
import jenkins.slaves.RemotingVersionInfo;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Objects;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Stephen Connolly
 */
public class AboutJenkinsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-56245")
    public void testAboutJenkinsContent() {
        
        String aboutMdToString = SupportTestUtils.invokeComponentToString(Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));

        assertThat(aboutMdToString, containsString("  * Instance ID: `" + j.getInstance().getLegacyInstanceId()));
        IdentityRootAction idRootaction = j.getInstance().getExtensionList(IdentityRootAction.class).get(0);
        assertThat(aboutMdToString, containsString(idRootaction.getPublicKey()));
        assertThat(aboutMdToString, containsString(idRootaction.getFingerprint()));
        assertThat(aboutMdToString, containsString("  * Embedded Version: `" + RemotingVersionInfo.getEmbeddedVersion().toString()));
        assertThat(aboutMdToString, containsString("  * Minimum Supported Version: `" + RemotingVersionInfo.getMinimumSupportedVersion().toString()));
    }

    @Test
    @Issue("JENKINS-65097")
    public void testAboutNodesContent() throws Exception {

        DumbSlave tcp1 = j.createSlave("tcp1", "test", null);
        tcp1.setLauncher(new JNLPLauncher(false));
        ((JNLPLauncher)tcp1.getLauncher()).setWebSocket(false);
        tcp1.save();
        
        String aboutMdToString = SupportTestUtils.invokeComponentToString(Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));
        assertThat(aboutMdToString, containsString("  * `" + tcp1.getNodeName() + "` (`hudson.slaves.DumbSlave`)"));
        assertThat(aboutMdToString, containsString("      - Launch method:  `hudson.slaves.JNLPLauncher`"));
        assertThat(aboutMdToString, containsString("      - WebSocket:      false"));
        
        ((JNLPLauncher)tcp1.getLauncher()).setWebSocket(true);
        tcp1.save();

        aboutMdToString = SupportTestUtils.invokeComponentToString(Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AboutJenkins.class)));
        assertThat(aboutMdToString, containsString("  * `" + tcp1.getNodeName() + "` (`hudson.slaves.DumbSlave`)"));
        assertThat(aboutMdToString, containsString("      - Launch method:  `hudson.slaves.JNLPLauncher`"));
        assertThat(aboutMdToString, containsString("      - WebSocket:      true"));
    }
}
