package com.cloudbees.jenkins.support;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.config.SupportAutomatedBundleConfiguration;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class CasCTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yaml")
    void assertConfiguredAsExpected(JenkinsConfiguredWithCodeRule r) {
        assertTrue(
                ContentFilters.get().isEnabled(),
                "JCasC should have configured support core to anonymize contents, but it didn't");
        assertTrue(
                SupportAutomatedBundleConfiguration.get().isEnabled(),
                "JCasC should have configured support period bundle generation enabled, but it didn't");
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
