/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.model.User;
import jenkins.security.LastGrantedAuthoritiesProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class UserCountTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-56245")
    public void testAboutJenkinsContent() throws Exception {
        User.getOrCreateByIdOrFullName("alice");
        User.getOrCreateByIdOrFullName("bob");
        User.getOrCreateByIdOrFullName("charlie");
        User.getOrCreateByIdOrFullName("dave").addProperty(new LastGrantedAuthoritiesProperty());
        User.getOrCreateByIdOrFullName("eve").addProperty(new LastGrantedAuthoritiesProperty());

        String usersMdToString = SupportTestUtils.invokeComponentToString(
                ExtensionList.lookup(Component.class).get(UserCount.class));
        assertThat(usersMdToString, containsString(" * Non Authenticated User Count: " + 3));
        assertThat(usersMdToString, containsString(" * Authenticated User Count: " + 2));
    }
}
