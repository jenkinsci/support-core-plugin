/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import jenkins.model.Jenkins;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OtherLogs.class, GCLogs.class, Jenkins.class})
// Need to ignore some packages due to Powermockito issue with JDK 11 https://github.com/powermock/powermock/issues/864
@PowerMockIgnore({ "javax.xml.*" })
public class OtherLogsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testOtherLogsContentEmpty() {
        String otherLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(OtherLogs.class)));
        assertTrue("Should not write anything", otherLogs.isEmpty());
    }

    @Test
    public void testOtherLogsRootDir() throws IOException {
        File testFile = new File(j.getInstance().getRootDir(), "test.log");
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"), 
                Charset.defaultCharset());
        
        String otherLogs = SupportTestUtils.invokeComponentToString(
                Objects.requireNonNull(ExtensionList.lookup(Component.class).get(OtherLogs.class)));
        assertFalse("Should collect *.log under the root dir", otherLogs.isEmpty());
        assertThat(otherLogs , Matchers.containsString("This is a test from root dir"));
    }

    @Test
    public void testOtherLogsExcludeGCSimpleFile() throws Exception {
        File testFile = new File(j.getInstance().getRootDir(), "test.log");
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"),
            Charset.defaultCharset());
        
        File tmpFile = File.createTempFile("gclogs", ".log", j.getInstance().getRootDir());
        Files.write(tmpFile.toPath(), Collections.singletonList("This is a GC file"));

        GCLogs.VmArgumentFinder finder = mockFinderAndGcLogs();

        if (SupportTestUtils.isJava8OrBelow()) {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH + tmpFile.getAbsolutePath());
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        } else {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=" + tmpFile.getAbsolutePath());
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));

            // The file locations may be wrapped with quotes
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\"" + tmpFile.getAbsolutePath()+ "\"");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        }
    }

    @Test
    public void testOtherLogsExcludeGCRotatedFiles() throws Exception {
        File rootDir = j.getInstance().getRootDir();
        File testFile = new File(rootDir, "test.log");
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"),
            Charset.defaultCharset());
        
        for (int count = 0; count < 5; count++) {
            File tmpFile = new File(rootDir, "gc.log." + count);
            Files.write(tmpFile.toPath(), Collections.singletonList("This is a GC file"));
        }

        GCLogs.VmArgumentFinder finder = mockFinderAndGcLogs();

        if(SupportTestUtils.isJava8OrBelow()) {
            when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH
                + rootDir.getAbsolutePath() + File.separator + "gc.log");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        } else {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file="
                + rootDir.getAbsolutePath() + File.separator + "gc.log.%p" + ":filecount=10,filesize=50m");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));

            // The file locations may be wrapped with quotes
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\""
                + rootDir.getAbsolutePath() + File.separator + "gc.log.%p\":filecount=10,filesize=50m");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        }
    }

    @Test
    public void testOtherLogsExcludeGCParameterizedFiles() throws Exception {
        File rootDir = j.getInstance().getRootDir();
        File testFile = new File(rootDir, "test.log");
        Files.createFile(testFile.toPath());
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"),
            Charset.defaultCharset());
        
        for (int count = 0; count < 5; count++) {
            File tmpFile = new File(rootDir, "gc." + System.currentTimeMillis() + ".1423.log." + count);
            Files.write(tmpFile.toPath(), Collections.singletonList("This is a GC file"));
        }

        GCLogs.VmArgumentFinder finder = mockFinderAndGcLogs();

        if(SupportTestUtils.isJava8OrBelow()) {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH
                + new File(rootDir, "gc.%t.%p.log").getAbsolutePath());
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        } else {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file="
                + rootDir.getAbsolutePath() + File.separator + "gc.%t.%p.log");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));

            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\""
                + rootDir.getAbsolutePath() + File.separator + "gc.%t.%p.log\"");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        }
    }
    
    @Test
    public void testOtherLogsExcludeGCParameterizedAndRotatedFiles() throws Exception {
        File rootDir = j.getInstance().getRootDir();
        File testFile = new File(rootDir, "test.log");
        Files.createFile(testFile.toPath());
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"),
            Charset.defaultCharset());
        
        for (int count = 0; count < 5; count++) {
            Files.write(new File(rootDir, "gc" + System.currentTimeMillis() + ".log." + count).toPath(), 
                Collections.singletonList("This is a GC file"));
        }
        
        GCLogs.VmArgumentFinder finder = mockFinderAndGcLogs();

        if(SupportTestUtils.isJava8OrBelow()) {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH 
                + new File(rootDir, "gc%p.log").getAbsolutePath());
            when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        } else {

            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file="
                + rootDir.getAbsolutePath() + File.separator + "gc%t.log.%p" + ":filecount=10,filesize=50m");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));

            // The file locations may be wrapped with quotes
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\""
                + rootDir.getAbsolutePath() + File.separator + "gc%t.log.%p\"" + ":filecount=10,filesize=50m");
            assertContentContainsFiles(Collections.singletonList("other-logs/test.log"));
        }
    }

    @Test
    public void testOtherLogsNotExcludeGCLogsDirIsNotInRootDir() throws Exception {
        File rootDir = j.getInstance().getRootDir();
        File testFile = new File(rootDir, "test.log");
        Files.write(testFile.toPath(), Collections.singletonList("This is a test from root dir"),
            Charset.defaultCharset());

        // Create GC Logs file at root
        for (int count = 0; count < 5; count++) {
            File tmpFile = new File(rootDir, "gc.log." + count);
            Files.write(tmpFile.toPath(), Collections.singletonList("This is a GC file"));
        }

        GCLogs.VmArgumentFinder finder = mockFinderAndGcLogs();

        // Configure GC Logs to go under $JENKINS_ROOT/gc/
        if(SupportTestUtils.isJava8OrBelow()) {
            when(finder.findVmArgument(GCLogs.GCLOGS_ROTATION_SWITCH)).thenReturn(GCLogs.GCLOGS_ROTATION_SWITCH);
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE_SWITCH
                + rootDir.getAbsolutePath() + File.separator + "gc" + File.separator + "gc.log");
            assertContentContainsFiles(Arrays.asList("other-logs/test.log", "other-logs/gc.log.0", "other-logs/gc.log.1",
                "other-logs/gc.log.2", "other-logs/gc.log.3", "other-logs/gc.log.4"));
        } else {
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file="
                + rootDir.getAbsolutePath() + File.separator + "gc" + File.separator + "gc.log.%p" + ":filecount=10,filesize=50m");
            assertContentContainsFiles(Arrays.asList("other-logs/test.log", "other-logs/gc.log.0", "other-logs/gc.log.1",
                "other-logs/gc.log.2", "other-logs/gc.log.3", "other-logs/gc.log.4"));

            // The file locations may be wrapped with quotes
            when(finder.findVmArgument(GCLogs.GCLOGS_JRE9_SWITCH)).thenReturn(GCLogs.GCLOGS_JRE9_SWITCH + "*:file=\""
                + rootDir.getAbsolutePath() + File.separator + "gc" + File.separator + "gc.log.%p\":filecount=10,filesize=50m");
            assertContentContainsFiles(Arrays.asList("other-logs/test.log", "other-logs/gc.log.0", "other-logs/gc.log.1",
                "other-logs/gc.log.2", "other-logs/gc.log.3", "other-logs/gc.log.4"));
        }
    }

    private void assertContentContainsFiles(Collection<String> fileNames) {
        Assertions.assertThat(SupportTestUtils.invokeComponentToMap(
            ExtensionList.lookupSingleton((Class<? extends Component>) OtherLogs.class)))
                .containsOnlyKeys(fileNames);
    }
    
    private GCLogs.VmArgumentFinder mockFinderAndGcLogs() {
        GCLogs.VmArgumentFinder finder = mock(GCLogs.VmArgumentFinder.class);
        GCLogs gcLogs = new GCLogs(finder);
        PowerMockito.mockStatic(Jenkins.class, Mockito.CALLS_REAL_METHODS);
        when(Jenkins.lookup(GCLogs.class)).thenReturn(gcLogs);
        return finder;
    }
}
