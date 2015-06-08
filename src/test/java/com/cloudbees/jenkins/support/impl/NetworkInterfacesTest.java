package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Enumeration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class NetworkInterfacesTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testGetNetworkInterface() throws Exception {
        // This machine might not have a network interface. But how did it get this code?
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        if (!networkInterfaces.hasMoreElements()) return;

        NetworkInterface networkInterface = networkInterfaces.nextElement();

        String expectedName = networkInterface.getDisplayName();

        NetworkInterfaces ni = new NetworkInterfaces();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ni.addContents(new Container() {
            @Override
            public void add(@CheckForNull Content content) {
                try {
                    content.writeTo(baos);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        String masterNetworkInterfaces = baos.toString();

        assertThat("Should at least contain one network interface.",
                masterNetworkInterfaces,
                containsString(expectedName));
    }
}