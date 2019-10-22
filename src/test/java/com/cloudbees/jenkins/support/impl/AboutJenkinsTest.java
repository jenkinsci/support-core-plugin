/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import jenkins.model.identity.IdentityRootAction;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
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
        String aboutMdToString = SupportTestUtils.invokeComponentToString(ExtensionList.lookup(Component.class).get(AboutJenkins.class));
        
        assertThat(aboutMdToString, containsString("  * Instance ID: `" + j.getInstance().getLegacyInstanceId()));
        IdentityRootAction idRootaction = j.getInstance().getExtensionList(IdentityRootAction.class).get(0);
        assertThat(aboutMdToString, containsString(idRootaction.getPublicKey()));
        assertThat(aboutMdToString, containsString(idRootaction.getFingerprint()));
    }
}
