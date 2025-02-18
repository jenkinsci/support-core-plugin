package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertFalse;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DumpExportTableTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @After
    public void after() throws InterruptedException, ExecutionException {
        for (var agent : j.jenkins.getNodes()) {
            System.err.println("Stopping " + agent);
            agent.toComputer().disconnect(null).get();
        }

        Thread.sleep(200);
        System.out.println("slept for 1 seconds -----");
    }

    @Test
    public void testAddContents() throws Exception {
        // Given
        DumbSlave onlineAgent = j.createOnlineSlave();

        // When
        String dumpTableString = SupportTestUtils.invokeComponentToString(new DumpExportTable());

        // Then
        assertFalse("Should have dumped the export table.", dumpTableString.isEmpty());

        List<String> output = new ArrayList<>(Arrays.asList(dumpTableString.split("\n")));
        assertThat(output, hasItems(containsString("hudson.remoting.ExportTable")));
    }

    @Test
    public void testLargeExportTableTruncated() throws Exception {
        // Given
        DumbSlave onlineAgent = j.createOnlineSlave();
        VirtualChannel channel = onlineAgent.getChannel();
        // This will generate an export table with 2MB of content.
        for (int i = 0; i < 35000; i++) {
            channel.export(MockSerializable.class, new MockSerializable() {});
        }

        // When
        String dumpTableString = SupportTestUtils.invokeComponentToString(new DumpExportTable());

        // Then
        assertThat(dumpTableString.length(), lessThanOrEqualTo(FileListCapComponent.MAX_FILE_SIZE));
    }

    public interface MockSerializable extends Serializable {}
}
