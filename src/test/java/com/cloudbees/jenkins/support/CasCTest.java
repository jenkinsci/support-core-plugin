package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.config.SupportAutomatedBundleConfiguration;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertTrue;

public class CasCTest extends RoundTripAbstractTest {

    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        assertTrue("JCasC should have configured support core to anonymize contents, but it didn't", 
            ContentFilters.get().isEnabled());
        assertTrue("JCasC should have configured support period bundle generation enabled, but it didn't", 
            SupportAutomatedBundleConfiguration.get().isEnabled());
        assertThat("JCasC should have configured support period bundle generation period, but it didn't",
            SupportAutomatedBundleConfiguration.get().getPeriod(),
            is(2));
        assertThat("JCasC should have configured support period bundle generation period, but it didn't",
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
                "PipelineThreadDump"
            ));
    }

    @Override
    protected String stringInLogExpected() {
        return ".enabled = true";
    }
}
