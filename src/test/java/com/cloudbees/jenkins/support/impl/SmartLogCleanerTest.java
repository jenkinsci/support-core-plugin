package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import hudson.ExtensionList;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SmartLogCleanerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void cleanUp() throws Exception {
        File supportDir = new File(j.getInstance().getRootDir(), "support");
        File cacheDir = new File(supportDir, "winsw");

        DumbSlave slave1 = j.createOnlineSlave();
        DumbSlave slave2 = j.createOnlineSlave();
        generateBundle();

        assertNotNull("The cache directory is empty", cacheDir.list());

        // wait for completion of SmartLogFetcher async tasks during the bundle generation
        for (int i = 0; i < 10; i++) {
            int cacheDirsCount = cacheDir.list().length;
            if (cacheDirsCount == 2) {
                break;
            } else {
                Thread.sleep(1000 * 10);
            }
        }

        assertEquals(cacheDir.list().length, 2);
        j.getInstance().removeNode(slave2);

        generateBundle();

        // wait for completion of SmartLogFetcher async tasks during the bundle generation
        for (int i = 0; i < 10; i++) {
            int cacheDirsCount = cacheDir.list().length;
            if (cacheDirsCount == 1) {
                break;
            } else {
                Thread.sleep(1000 * 10);
            }
        }

        assertEquals(cacheDir.list().length, 1);

    }

    private ZipFile generateBundle() throws IOException {
        List<Component> componentsToCreate = Collections.singletonList(ExtensionList.lookup(Component.class).get(SlaveLogs.class));
        File bundleFile = temp.newFile();
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(false);
            SupportPlugin.writeBundle(os, componentsToCreate);
            return new ZipFile(bundleFile);
        }
    }
}
