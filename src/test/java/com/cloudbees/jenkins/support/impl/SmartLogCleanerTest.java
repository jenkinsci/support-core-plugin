package com.cloudbees.jenkins.support.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import hudson.ExtensionList;
import hudson.slaves.DumbSlave;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SmartLogCleanerTest {

    @TempDir
    private File temp;

    @Test
    void cleanUp(JenkinsRule j) throws Exception {
        File cacheDir = new File(SupportPlugin.getLogsDirectory(), "winsw");

        DumbSlave agent1 = j.createOnlineSlave();
        DumbSlave agent2 = j.createOnlineSlave();
        generateBundle();

        assertNotNull(cacheDir.list(), "The cache directory is empty");

        // wait for completion of SmartLogFetcher async tasks during the bundle generation
        for (int i = 0; i < 10; i++) {
            int cacheDirsCount = cacheDir.list().length;
            if (cacheDirsCount == 2) {
                break;
            } else {
                Thread.sleep(1000 * 10);
            }
        }

        assertEquals(2, cacheDir.list().length);
        agent2.toComputer().disconnect(null).get();
        j.getInstance().removeNode(agent2);

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

        assertEquals(1, cacheDir.list().length);
    }

    private ZipFile generateBundle() throws IOException {
        List<Component> componentsToCreate =
                Collections.singletonList(ExtensionList.lookup(Component.class).get(SlaveLogs.class));
        File bundleFile = File.createTempFile("junit", null, temp);
        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            ContentFilters.get().setEnabled(false);
            SupportPlugin.writeBundle(os, componentsToCreate);
            return new ZipFile(bundleFile);
        }
    }
}
