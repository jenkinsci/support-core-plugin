package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.io.Files;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GCLogsTest {

    @Test
    public void simpleFile() throws Exception {
        File tmpFile = File.createTempFile("gclogs", "");
        Files.touch(tmpFile);

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH + tmpFile.getAbsolutePath());

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(1, container.getContents().size());
    }

    @Test
    public void rotatedFiles() throws Exception {
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH + tempDir.getAbsolutePath()+"/gc.log");

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(5, container.getContents().size());
    }

    @Test
    public void parameterizedFiles() throws Exception {
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".1423.log." + count));
        }
        for (int count = 0; count < 3; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".2534.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH + new File(tempDir, "gc.%t.%p.log.%n").getAbsolutePath());

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(8, container.getContents().size());
    }

    @Test
    public void parameterizedAndRotatedFiles() throws Exception {
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 10; count++) {
            Files.touch(new File(tempDir, "gc5625.log." + count));
        }
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc3421.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH + new File(tempDir, "gc%p.log").getAbsolutePath());
        when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(15, container.getContents().size());
    }

    private static class TestContainer extends Container {
        List<Content> contents = new ArrayList<Content>();

        public List<Content> getContents() {
            return contents;
        }

        @Override
        public void add(@CheckForNull Content content) {
            contents.add(content);
        }
    }
}
