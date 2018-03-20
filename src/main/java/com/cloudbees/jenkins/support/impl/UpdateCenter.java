package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.ContentData;
import com.ning.http.client.ProxyServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.plugins.asynchttpclient.AHCUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;

/**
 * Add information about the different update centers available to
 * the Jenkins instance.
 *
 * @since 2.30
 */
@Extension
public class UpdateCenter extends Component {
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Update Center";
    }

    @Override
    public void addContents(@NonNull Container container) {
        addContents(container, false);
    }

    @Override
    public void addContents(@NonNull Container container, boolean shouldAnonymize) {
        container.add(new Content(new ContentData("update-center.md", shouldAnonymize)) {
                    @Override
                    public void writeTo(OutputStream os) throws IOException {
                        PrintWriter out = getPrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                        try {
                            hudson.model.UpdateCenter updateCenter = Jenkins.getInstance().getUpdateCenter();
                            out.println("=== Sites ===");
                            for (UpdateSite c : updateCenter.getSiteList()) {
                                out.println(" - Url: " + c.getUrl());
                                out.println(" - Connection Url: " + c.getConnectionCheckUrl());
                                out.println(" - Implementation Type: " + c.getClass().getName());
                            }

                            out.println("======");

                            out.println("Last updated: " + updateCenter.getLastUpdatedString());

                            // Only do this part of the async-http-client plugin is installed.
                            if (Jenkins.getInstance().getPlugin("async-http-client") != null) {
                                addProxyInformation(out);
                            } else {
                                out.println("Proxy: 'async-http-client' not installed, so no proxy info available.");
                            }
                        } finally {
                            out.flush();
                        }
                    }
                }
        );
    }

    private void addProxyInformation(PrintWriter out) {
        out.println("=== Proxy ===");
        ProxyServer proxyServer = AHCUtils.getProxyServer();
        if (proxyServer != null) {
            out.println(" - Host: " + proxyServer.getHost());
            out.println(" - Port: " + proxyServer.getPort());

            out.println(" - No Proxy Hosts: ");
            for (String noHost : proxyServer.getNonProxyHosts()) {
                out.println(" * " + noHost);
            }
        }
    }
}
