package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class DumpExportTableTest {
  @Rule public JenkinsRule j = new JenkinsRule();
  
  @Test
  public void testAddContents() throws Exception {
    // Given
    DumbSlave onlineSlave = j.createOnlineSlave();

    // When
    String dumpTableString = SupportTestUtils.invokeComponentToString(new DumpExportTable());

    // Then
    assertFalse("Should have dumped the export table.",
            dumpTableString.isEmpty());

    List<String> output = new ArrayList<String>(Arrays.asList(dumpTableString.split("\n")));
    assertThat(output, hasItems(containsString("hudson.remoting.ExportTable")));
  }

  @Test
  public void testLargeExportTableTruncated() throws Exception {
    // Given
    DumbSlave onlineSlave = j.createOnlineSlave();
    VirtualChannel channel = onlineSlave.getChannel();
    // This will generate an export table with 2MB of content.
    for (int i = 0; i < 35000; i++) {
      channel.export(MockSerializable.class, new MockSerializable() {
      });
    }

    // When
    String dumpTableString = SupportTestUtils.invokeComponentToString(new DumpExportTable());

    // Then
    assertThat(dumpTableString.length(), lessThanOrEqualTo(FileListCapComponent.MAX_FILE_SIZE));
  }

  public interface MockSerializable extends Serializable {}
}