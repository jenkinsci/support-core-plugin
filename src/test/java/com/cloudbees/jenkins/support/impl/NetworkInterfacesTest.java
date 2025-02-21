package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class NetworkInterfacesTest {

    @Test
    void testGetNetworkInterface(JenkinsRule j) throws Exception {
        // This machine might not have a network interface. But how did it get this code?
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        if (!networkInterfaces.hasMoreElements()) return;

        NetworkInterface networkInterface = networkInterfaces.nextElement();

        String expectedName = networkInterface.getDisplayName();

        NetworkInterfaces ni = new NetworkInterfaces();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
        String controllerNetworkInterfaces = baos.toString();

        assertThat(
                "Should at least contain one network interface.",
                controllerNetworkInterfaces,
                containsString(expectedName));
    }
}
