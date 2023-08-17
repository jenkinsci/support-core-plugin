package com.cloudbees.jenkins.support;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertTrue;

import com.cloudbees.jenkins.support.config.SupportAutomatedBundleConfiguration;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;

public class CasCTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yaml")
    public void assertConfiguredAsExpected() {
        assertTrue(
                "JCasC should have configured support core to anonymize contents, but it didn't",
                ContentFilters.get().isEnabled());
        assertTrue(
                "JCasC should have configured support period bundle generation enabled, but it didn't",
                SupportAutomatedBundleConfiguration.get().isEnabled());
        assertThat(
                "JCasC should have configured support period bundle generation period, but it didn't",
                SupportAutomatedBundleConfiguration.get().getPeriod(),
                is(2));
        assertThat(
                "JCasC should have configured support period bundle generation period, but it didn't",
                SupportAutomatedBundleConfiguration.get().getComponentIds(),
                containsInAnyOrder(
                        "AboutBrowser",
                        "AboutJenkins",
                        "AboutUser",
                        "AdministrativeMonitors",
                        "AgentProtocols",
                        "BuildQueue",
                        "CustomLogs",
                        "DumpExportTable",
                        "EnvironmentVariables",
                        "FileDescriptorLimit",
                        "ItemsContent",
                        "ControllerJVMProcessSystemMetricsContents",
                        "JenkinsLogs",
                        "LoadStats",
                        "LoggerManager",
                        "Metrics",
                        "NetworkInterfaces",
                        "NodeMonitors",
                        "ReverseProxy",
                        "RunningBuilds",
                        "AgentCommandStatistics",
                        "ControllerSystemConfiguration",
                        "SystemProperties",
                        "TaskLogs",
                        "ThreadDumps",
                        "UpdateCenter",
                        "UserCount",
                        "SlowRequestComponent",
                        "DeadlockRequestComponent",
                        "PipelineTimings",
                        "PipelineThreadDump"));
    }
}
