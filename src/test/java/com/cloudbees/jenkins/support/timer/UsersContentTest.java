/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.timer;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.impl.UsersContent;
import hudson.ExtensionList;
import hudson.model.User;
import jenkins.security.LastGrantedAuthoritiesProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class UsersContentTest {

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
                
        String usersMdToString = SupportTestUtils.invokeComponentToString(ExtensionList.lookup(Component.class).get(UsersContent.class));
        assertThat(usersMdToString, containsString(" * Non Authenticated Users count: " + 3));
        assertThat(usersMdToString, containsString(" * Authenticated Users count: " + 2));
    }
}
