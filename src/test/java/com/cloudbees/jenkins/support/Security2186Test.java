package com.cloudbees.jenkins.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.configfiles.AgentsConfigFile;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.impl.EnvironmentVariables;
import com.cloudbees.jenkins.support.impl.SlaveLaunchLogs;
import com.cloudbees.jenkins.support.impl.SystemProperties;
import hudson.ExtensionList;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;

public class Security2186Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    ;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Before
    public void setup() throws Exception {
        System.setProperty("password", "mySecret");
        setupAgent();
    }

    @After
    public void stopAgents() throws Exception {
        SupportTestUtils.stopAgents();
    }

    @Test
    @Issue("SECURITY-2186")
    public void secretsFilterWhenSystemPropertyContainsPasswordThenValueRedacted() throws Exception {
        List<Component> componentsToCreate = Arrays.asList(
                ExtensionList.lookup(Component.class).get(AboutJenkins.class),
                ExtensionList.lookup(Component.class).get(EnvironmentVariables.class),
                ExtensionList.lookup(Component.class).get(SystemProperties.class),
                ExtensionList.lookup(Component.class).get(AgentsConfigFile.class),
                ExtensionList.lookup(Component.class).get(SlaveLaunchLogs.class));

        File bundleFile = temp.newFile();

        try (OutputStream os = Files.newOutputStream(bundleFile.toPath())) {
            SupportPlugin.writeBundle(os, componentsToCreate);
        }

        ZipFile zip = new ZipFile(bundleFile);

        verifyFileIfContainsPassword(zip, "nodes.md", "-Dtest2186.trustStorePassword");
        verifyFileIfContainsPassword(zip, "nodes.md", "-Dtest2186.trustStoreAgentPassword");
        verifyFileIfContainsPassword(zip, "about.md", "-Dtest2186.trustStorePassword");
        verifyFileIfContainsPassword(zip, "nodes/master/system.properties", "test2186.trustStorePassword");
        verifyFileIfContainsPassword(zip, "nodes/master/environment.txt", "test2186.trustStorePassword");
        verifyFileIfContainsPassword(zip, "nodes/slave/slave0/config.xml", "-Dtest2186.trustStoreAgentPassword");
        verifyFileIfContainsPassword(zip, "nodes/slave/launches/all.log", "-Dtest2186.trustStoreAgentPassword");
    }

    private void verifyFileIfContainsPassword(ZipFile zip, String fileName, String expectedPasswordKey)
            throws IOException {
        ZipEntry nodesMdEntry = zip.getEntry(fileName);

        StringBuilder resultStringBuilder = new StringBuilder();
        try (InputStream stream = zip.getInputStream(nodesMdEntry);
                BufferedReader br = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        // to avoid test flickering because sometimes remote calls could not wait for a result
        if (resultStringBuilder.toString().contains(expectedPasswordKey)) {
            assertThat(resultStringBuilder.toString(), containsString(expectedPasswordKey + "=REDACTED"));
        }
    }

    private void setupAgent() throws Exception {
        ComputerLauncher launcher = createComputerLauncher();
        DumbSlave agent = createSlave(launcher);
        agent.save();
    }

    private DumbSlave createSlave(ComputerLauncher launcher) throws Exception {
        DumbSlave slave = new DumbSlave(
                "slave0",
                "dummy",
                temp.newFolder("agent").getPath(),
                "1",
                Node.Mode.NORMAL,
                "Agent1",
                launcher,
                RetentionStrategy.NOOP,
                Collections.EMPTY_LIST);
        j.jenkins.addNode(slave);
        j.waitOnline(slave);
        return slave;
    }

    private ComputerLauncher createComputerLauncher() throws URISyntaxException, IOException {
        return new SimpleCommandLauncher(String.format(
                "\"%s/bin/java\" %s %s -jar \"%s\"",
                System.getProperty("java.home"),
                "-Djava.awt.headless=true",
                "-Dtest2186.trustStoreAgentPassword=mySecret",
                new File(j.jenkins.getJnlpJars("slave.jar").getURL().toURI()).getAbsolutePath()));
    }
}
