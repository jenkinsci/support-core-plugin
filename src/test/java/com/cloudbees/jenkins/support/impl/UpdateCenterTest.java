package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.model.UpdateSite;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class UpdateCenterTest {

    @Test
    void testUpdateCenterContent(JenkinsRule j) {
        String ucMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(UpdateCenter.class)));
        for (UpdateSite site : j.jenkins.getUpdateCenter().getSiteList()) {
            assertThat(ucMdToString, containsString(" - Id: " + site.getId()));
            assertThat(ucMdToString, containsString(" - Url: " + site.getUrl()));
            assertThat(ucMdToString, containsString(" - Connection Url: " + site.getConnectionCheckUrl()));
            assertThat(
                    ucMdToString,
                    containsString(" - Implementation Type: " + site.getClass().getName()));
        }
    }
}
