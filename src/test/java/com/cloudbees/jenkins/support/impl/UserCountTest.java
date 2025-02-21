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
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UserCountTest {

    @Test
    @Issue("JENKINS-56245")
    void testAboutJenkinsContent(JenkinsRule j) throws Exception {
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
