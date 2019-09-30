package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.ning.http.client.ProxyServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.plugins.asynchttpclient.AHCUtils;

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
        container.add(new PrefilteredPrintedContent("update-center.md") {
                    @Override
                    public void printTo(PrintWriter out, ContentFilter filter) {
                        try {
                            hudson.model.UpdateCenter updateCenter = Jenkins.get().getUpdateCenter();
                            out.println("=== Sites ===");
                            for (UpdateSite c : updateCenter.getSiteList()) {
                                out.println(" - Url: " + ContentFilter.filter(filter, c.getUrl()));
                                out.println(" - Connection Url: " + ContentFilter.filter(filter, c.getConnectionCheckUrl()));
                                out.println(" - Implementation Type: " + c.getClass().getName());
                            }

                            out.println("======");

                            out.println("Last updated: " + updateCenter.getLastUpdatedString());

                            // Only do this part of the async-http-client plugin is installed.
                            if (Jenkins.get().getPlugin("async-http-client") != null) {
                                addProxyInformation(out, filter);
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

    private void addProxyInformation(PrintWriter out, ContentFilter filter) {
        out.println("=== Proxy ===");
        ProxyServer proxyServer = AHCUtils.getProxyServer();
        if (proxyServer != null) {
            out.println(" - Host: " + ContentFilter.filter(filter, proxyServer.getHost()));
            out.println(" - Port: " + proxyServer.getPort());

            out.println(" - No Proxy Hosts: ");
            for (String noHost : proxyServer.getNonProxyHosts()) {
                out.println(" * " + ContentFilter.filter(filter, noHost));
            }
        }
    }
}
