package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.diagnosis.ReverseProxySetupMonitor;
import hudson.model.AdministrativeMonitor;
import hudson.model.FreeStyleProject;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AdministrativeMonitorsTest {

    @Test
    void testAdministrativeMonitorsContent(JenkinsRule j) throws IOException {
        String monitorsMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AdministrativeMonitors.class)));
        // Enable all monitors and check that there is an output for all activated monitors
        j.jenkins.administrativeMonitors.stream()
                .filter(monitor -> !(monitor instanceof ReverseProxySetupMonitor || monitor instanceof OldDataMonitor))
                .filter(monitor -> monitor.isEnabled() && monitor.isActivated())
                .forEach(monitor -> assertThat(
                        monitorsMdToString,
                        containsString("`" + monitor.id + "`" + System.lineSeparator() + "--------------"
                                + System.lineSeparator() + "(active and enabled)")));

        // Disable all monitors and check there is not output for any even if activated
        for (AdministrativeMonitor monitor : j.jenkins.administrativeMonitors) {
            monitor.disable(true);
        }
        String monitorsDisabledMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AdministrativeMonitors.class)));
        j.jenkins.administrativeMonitors.stream()
                .filter(monitor -> !(monitor instanceof ReverseProxySetupMonitor || monitor instanceof OldDataMonitor))
                .filter(AdministrativeMonitor::isActivated)
                .forEach(monitor -> assertThat(
                        monitorsDisabledMdToString,
                        not(containsString("`" + monitor.id + "`" + System.lineSeparator() + "--------------"
                                + System.lineSeparator() + "(active and enabled)"))));
    }

    @Test
    void testOldDataMonitorAnonymized(JenkinsRule j) throws IOException {
        ContentFilters.get().setEnabled(true);
        FreeStyleProject p = j.createFreeStyleProject("sensitive-job-name");
        ContentFilter filter = SupportPlugin.getDefaultContentFilter();
        OldDataMonitor.report(p, "1.234");
        String monitorsMdToString = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(AdministrativeMonitors.class)),
                SupportPlugin.getDefaultContentFilter());
        // Assert that there is an output for OldDataMonitor
        assertThat(
                monitorsMdToString,
                containsString(
                        "`" + Objects.requireNonNull(AdministrativeMonitor.all().get(OldDataMonitor.class)).id + "`"));
        assertThat(monitorsMdToString, not(containsString("sensitive-job-name")));
    }
}
