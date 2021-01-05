package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.diagnosis.ReverseProxySetupMonitor;
import hudson.model.AdministrativeMonitor;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Objects;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class AdministrativeMonitorsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testAdministrativeMonitorsContent() throws IOException {
        String monitorsMdToString = SupportTestUtils.invokeComponentToString(Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AdministrativeMonitors.class)));
        // Enable all monitors and check that there is an output for all activated monitors
        j.jenkins.administrativeMonitors.stream()
            .filter(monitor -> !(monitor instanceof ReverseProxySetupMonitor || monitor instanceof OldDataMonitor))
            .filter(monitor -> monitor.isEnabled() && monitor.isActivated())
            .forEach(monitor -> 
                assertThat(monitorsMdToString, containsString(
                    "`" + monitor.id + "`\n" +
                        "--------------\n" +
                        "(active and enabled)"))
            );

        // Disable all monitors and check there is not output for any even if activated
        for (AdministrativeMonitor monitor : j.jenkins.administrativeMonitors) {
            monitor.disable(true);
        }
        String monitorsDisabledMdToString = SupportTestUtils.invokeComponentToString(Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AdministrativeMonitors.class)));
        j.jenkins.administrativeMonitors.stream()
            .filter(monitor -> !(monitor instanceof ReverseProxySetupMonitor || monitor instanceof OldDataMonitor))
            .filter(AdministrativeMonitor::isActivated)
            .forEach(monitor ->
                assertThat(monitorsDisabledMdToString, not(containsString(
                    "`" + monitor.id + "`\n" +
                        "--------------\n" +
                        "(active and enabled)")))
            );
    }
}
