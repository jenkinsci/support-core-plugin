package com.cloudbees.jenkins.support.actions;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.util.SystemPlatform;
import hudson.model.Computer;
import hudson.model.Item;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Allan Burdajewicz
 */
public class SupportComputerActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @After
    public void stopAgents() throws Exception {
        for (var agent : j.jenkins.getNodes()) {
            System.err.println("Stopping " + agent);
            agent.toComputer().disconnect(null).get();
        }
    }

    @Test
    public void onlyAdminCanSeeAction() throws Exception {

        Computer c = j.createSlave("slave1", "test", null).getComputer();

        SupportComputerAction pAction = new SupportComputerAction(j.jenkins.getComputer("slave1"));

        SupportTestUtils.testPermissionToSeeAction(
                j,
                Objects.requireNonNull(c).getUrl(),
                pAction,
                Stream.of(Jenkins.ADMINISTER).collect(Collectors.toSet()),
                Stream.of(Jenkins.READ, Item.READ, SupportPlugin.CREATE_BUNDLE).collect(Collectors.toSet()));

        SupportTestUtils.testPermissionToDisplayAction(
                j,
                c.getUrl(),
                pAction,
                Stream.of(Jenkins.ADMINISTER).collect(Collectors.toSet()),
                Stream.of(Jenkins.READ, Item.READ, SupportPlugin.CREATE_BUNDLE).collect(Collectors.toSet()));
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     */
    @Test
    public void generateBundleDefaultsAndCheckContent() throws Exception {
        j.createSlave("agent1", "test", null).getComputer().connect(false).get();

        // Check that the generation does not show any warnings
        Computer computer = j.jenkins.getComputer("agent1");
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                computer.getUrl(),
                new SupportComputerAction(j.jenkins.getComputer("agent1")),
                SupportPlugin.class,
                j.createWebClient());

        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry("nodes/slave/agent1/config.xml"));

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
                assertNotNull(file + " was not found in the bundle", z.getEntry("nodes/slave/agent1/" + file));
            }
        }
    }
}
