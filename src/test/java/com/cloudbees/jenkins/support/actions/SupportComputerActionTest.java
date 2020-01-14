package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import hudson.model.Computer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;

/**
 * @author Allan Burdajewicz
 */
public class SupportComputerActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     */
    @Test
    public void generateBundleDefaultsAndCheckContent() throws Exception {
        j.createSlave("slave1", "test", null).getComputer().connect(false).get();

        // Check that the generation does not show any warnings
        Computer computer = j.jenkins.getComputer("slave1");
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                computer.getUrl(),
                new SupportComputerAction(j.jenkins.getComputer("slave1")),
                SupportPlugin.class,
                j.createWebClient());

        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry("nodes/slave/slave1/config.xml"));

        if (SystemPlatform.LINUX == SystemPlatform.current()) {
            List<String> files = Arrays.asList(
                    "proc/swaps.txt",
                    "proc/cpuinfo.txt",
                    "proc/mounts.txt",
                    "proc/system-uptime.txt",
                    "proc/net/rpc/nfs.txt",
                    "proc/net/rpc/nfsd.txt",
                    "proc/meminfo.txt",
                    "proc/self/status.txt",
                    "proc/self/cmdline",
                    "proc/self/environ",
                    "proc/self/limits.txt",
                    "proc/self/mountstats.txt",
                    "sysctl.txt",
                    "dmesg.txt",
                    "userid.txt",
                    "dmi.txt");

            for (String file : files) {
                assertNotNull(file + " was not found in the bundle",
                        z.getEntry("nodes/slave/slave1/" + file));
            }
        }
    }
}
