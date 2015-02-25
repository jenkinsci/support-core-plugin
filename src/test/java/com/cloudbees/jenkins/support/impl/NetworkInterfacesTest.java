package com.cloudbees.jenkins.support.impl;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.NetworkInterface;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class NetworkInterfacesTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testGetNetworkInterface() throws Exception {
        // This machine might not have a network interface. But how did it get this code?
        if (!NetworkInterface.getNetworkInterfaces().hasMoreElements()) return;

        NetworkInterface networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
        String expectedName = networkInterface.getDisplayName();

        NetworkInterfaces ni = new NetworkInterfaces();
        String masterNetworkInterfaces = ni.getNetworkInterface(r.jenkins);
        System.out.printf(masterNetworkInterfaces);

        assertThat("Should at least contain one network interface.",
                masterNetworkInterfaces,
                containsString(expectedName));
    }
}