package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DumpExportTableTest {

    @Test
    void testAddContents(JenkinsRule j) throws Exception {
        // Given
        DumbSlave onlineAgent = j.createOnlineSlave();

        // When
        String dumpTableString = SupportTestUtils.invokeComponentToString(new DumpExportTable());

        // Then
        assertFalse(dumpTableString.isEmpty(), "Should have dumped the export table.");

        List<String> output = new ArrayList<>(Arrays.asList(dumpTableString.split("\n")));
        assertThat(output, hasItems(containsString("hudson.remoting.ExportTable")));
    }

    @Test
    void testLargeExportTableTruncated(JenkinsRule j) throws Exception {
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

    private interface MockSerializable extends Serializable {}
}
