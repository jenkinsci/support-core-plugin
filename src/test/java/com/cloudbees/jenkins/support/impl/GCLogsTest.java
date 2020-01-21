package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.io.Files;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GCLogsTest {

    @Test
    public void simpleFile() throws Exception {
        Assume.assumeTrue(SupportTestUtils.isJava8OrBelow());
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
        Assume.assumeTrue(SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH 
                + tempDir.getAbsolutePath() + File.separator + "gc.log");

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(5, container.getContents().size());
    }

    @Test
    public void rotatedJava9Files() throws Exception {
        Assume.assumeTrue(!SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" 
            + tempDir.getAbsolutePath() + File.separator + "gc.log.%p" + ":filecount=10,filesize=50m");

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(5, container.getContents().size());
    }

    @Test
    public void rotatedJava9FilesWithQuotes() throws Exception {
        Assume.assumeTrue(!SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        // The file locations may be wrapped with quotes
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" 
                + tempDir.getAbsolutePath() + File.separator + "gc.log.%p\":filecount=10,filesize=50m");

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(5, container.getContents().size());
    }

    @Test
    public void parameterizedFiles() throws Exception {
        Assume.assumeTrue(SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".1423.log." + count));
        }
        for (int count = 0; count < 3; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".2534.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH
                + new File(tempDir, "gc.%t.%p.log").getAbsolutePath());

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(8, container.getContents().size());
    }

    @Test
    public void parameterizedJava9Files() throws Exception {
        Assume.assumeTrue(!SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".1423.log." + count));
        }
        for (int count = 0; count < 3; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".2534.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" 
                + tempDir.getAbsolutePath() + File.separator + "gc.%t.%p.log");

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(8, container.getContents().size());
    }

    @Test
    public void parameterizedJava9FilesWithQuotes() throws Exception {
        Assume.assumeTrue(!SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".1423.log." + count));
        }
        for (int count = 0; count < 3; count++) {
            Files.touch(new File(tempDir, "gc." + System.currentTimeMillis() + ".2534.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        // The file locations may be wrapped with quotes
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" 
                + tempDir.getAbsolutePath() + File.separator + "gc.%t.%p.log\"");

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(8, container.getContents().size());
    }

    @Test
    public void parameterizedAndRotatedFiles() throws Exception {
        Assume.assumeTrue(SupportTestUtils.isJava8OrBelow());
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

    @Test
    @Issue("JENKINS-58980")
    public void latestFiles() throws Exception {
        Assume.assumeTrue(SupportTestUtils.isJava8OrBelow());
        File tempDir = Files.createTempDir();
        long currentTime = System.currentTimeMillis();
        for (int count = 0; count < 10; count++) {
            File gcLogFile = new File(tempDir, "gc5625.log" + count);
            Files.touch(gcLogFile);
            gcLogFile.setLastModified(currentTime - TimeUnit.DAYS.toMillis(5));
        }
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc3421.log" + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH + new File(tempDir, "gc%p.log").getAbsolutePath());
        when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(5, container.getContents().size());
        Assertions.assertThat(container.getContents())
                .extracting("file", File.class)
                .extractingResultOf("getName")
                .contains("gc3421.log0", "gc3421.log1", "gc3421.log2", "gc3421.log3", "gc3421.log4");
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
