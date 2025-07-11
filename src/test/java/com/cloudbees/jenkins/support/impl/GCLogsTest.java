package com.cloudbees.jenkins.support.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.io.Files;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

public class GCLogsTest {

    @Test
    public void simpleFile() throws Exception {
        File tmpFile = File.createTempFile("gclogs", "");
        Files.touch(tmpFile);

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class, Mockito.CALLS_REAL_METHODS);

        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" + tmpFile.getAbsolutePath());
        assertContentWithFinderContainsFiles(finder, 1);

        // The file locations may be wrapped with quotes
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" + tmpFile.getAbsolutePath() + "\"");
        assertContentWithFinderContainsFiles(finder, 1);
    }

    @Test
    public void rotatedFiles() throws Exception {
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc.log." + count));
        }

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class, Mockito.CALLS_REAL_METHODS);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" + tempDir.getAbsolutePath() + File.separator
                        + "gc.log" + ":filecount=10,filesize=50m");
        assertContentWithFinderContainsFiles(finder, 5);

        // The file locations may be wrapped with quotes
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" + tempDir.getAbsolutePath() + File.separator
                        + "gc.log\":filecount=10,filesize=50m");
        assertContentWithFinderContainsFiles(finder, 5);
    }

    @Test
    public void parameterizedFiles() throws Exception {
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc." + System.nanoTime() + ".1423.log"));
        }
        for (int count = 0; count < 3; count++) {
            Files.touch(new File(tempDir, "gc." + System.nanoTime() + ".2534.log"));
        }
        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" + tempDir.getAbsolutePath() + File.separator
                        + "gc.%t.%p.log");
        assertContentWithFinderContainsFiles(finder, 8);

        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" + tempDir.getAbsolutePath() + File.separator
                        + "gc.%t.%p.log\"");
        assertContentWithFinderContainsFiles(finder, 8);
    }

    @Test
    public void parameterizedAndRotatedFiles() throws Exception {
        File tempDir = Files.createTempDir();
        for (int count = 0; count < 10; count++) {
            Files.touch(new File(tempDir, "gc." + System.nanoTime() + ".5625.log." + count));
        }
        for (int count = 0; count < 5; count++) {
            Files.touch(new File(tempDir, "gc." + System.nanoTime() + ".3421.log." + count));
        }
        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);

        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" + tempDir.getAbsolutePath() + File.separator
                        + "gc%t.log.%p" + ":filecount=10,filesize=50m");
        assertContentWithFinderContainsFiles(finder, 15);

        // The file locations may be wrapped with quotes
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" + tempDir.getAbsolutePath() + File.separator
                        + "gc%t.log.%p\"" + ":filecount=10,filesize=50m");
        assertContentWithFinderContainsFiles(finder, 15);
    }

    @Test
    @Issue("JENKINS-58980")
    public void latestFiles() throws Exception {
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

        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" + tempDir.getAbsolutePath() + File.separator
                        + "gc%t.log%p" + ":filecount=10,filesize=50m");
        assertContentWithFinderContainsFiles(
                finder, Arrays.asList("gc3421.log0", "gc3421.log1", "gc3421.log2", "gc3421.log3", "gc3421.log4"));

        // The file locations may be wrapped with quotes
        when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH))
                .thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" + tempDir.getAbsolutePath() + File.separator
                        + "gc%t.log%p\"" + ":filecount=10,filesize=50m");
        assertContentWithFinderContainsFiles(
                finder, Arrays.asList("gc3421.log0", "gc3421.log1", "gc3421.log2", "gc3421.log3", "gc3421.log4"));
    }

    /**
     * Assert that a specific number of files is included in the content, given the
     * {@link com.cloudbees.jenkins.support.impl.GCLogs.VmArgumentFinder} passed in.
     *
     * @param finder a {@link com.cloudbees.jenkins.support.impl.GCLogs.VmArgumentFinder}
     * @param numberOfFiles the expected number of files to be included
     */
    private void assertContentWithFinderContainsFiles(GCLogs.VmArgumentFinder finder, int numberOfFiles) {
        TestContainer container = new TestContainer();
        new GCLogs(finder).addContents(container);
        assertEquals(numberOfFiles, container.getContents().size());
    }

    /**
     * Assert that a specific set of file is included in the content, given the
     * {@link com.cloudbees.jenkins.support.impl.GCLogs.VmArgumentFinder} passed in.
     *
     * @param finder a {@link com.cloudbees.jenkins.support.impl.GCLogs.VmArgumentFinder}
     * @param fileNames the expected set of files names
     */
    private void assertContentWithFinderContainsFiles(GCLogs.VmArgumentFinder finder, Collection<String> fileNames) {
        TestContainer container = new TestContainer();
        new GCLogs(finder).addContents(container);
        assertEquals(fileNames.size(), container.getContents().size());
        Assertions.assertThat(container.getContents())
                .extracting("file", File.class)
                .extractingResultOf("getName")
                .containsAll(fileNames);
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
