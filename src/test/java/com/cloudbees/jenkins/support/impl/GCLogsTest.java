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
    public void testSimpleFile() throws Exception {
        File tmpFile = File.createTempFile("gclogs", "");
        Files.touch(tmpFile);

        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        System.out.println(tmpFile.exists());
        System.out.println(tmpFile);
        when(finder.findVmArgument(GCLogs.GCLOG_JRE_SWITCH)).thenReturn(GCLogs.GCLOG_JRE_SWITCH + tmpFile.getAbsolutePath());

        TestContainer container = new TestContainer();

        new GCLogs(finder).addContents(container);

        assertEquals(1, container.getContents().size());
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
