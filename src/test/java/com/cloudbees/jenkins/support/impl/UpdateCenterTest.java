package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.ProxyConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class UpdateCenterTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-68008")
    public void testUpdateCenterProxyContent() {

        List<String> noProxyHosts = Arrays.asList(".server.com", "*.example.com");
        j.jenkins
                .get()
                .setProxy(new ProxyConfiguration(
                        "proxy.server.com",
                        1234,
                        "proxyUser",
                        "proxyPass",
                        String.join("\n", noProxyHosts),
                        "http://localhost:8080"));

        String ucMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(UpdateCenter.class)));
        assertThat(ucMdToString, containsString(" - Host: proxy.server.com"));
        assertThat(ucMdToString, containsString(" - Port: 1234"));
        assertThat(ucMdToString, not(containsString("proxyUser")));
        assertThat(ucMdToString, not(containsString("proxyPass")));
        for (String noProxyHost : noProxyHosts) {
            assertThat(ucMdToString, containsString(" * " + noProxyHost));
        }
    }
}
