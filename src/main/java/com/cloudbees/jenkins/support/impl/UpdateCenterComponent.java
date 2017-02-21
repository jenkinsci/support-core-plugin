package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.MarkdownContent;
import com.cloudbees.jenkins.support.api.YamlContent;
import com.cloudbees.jenkins.support.model.UpdateCenter;
import com.cloudbees.jenkins.support.util.Helper;
import com.ning.http.client.ProxyServer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.plugins.asynchttpclient.AHCUtils;

import java.util.Collections;
import java.util.Set;

/**
 * Add information about the different update centers available to
 * the Jenkins instance.
 *
 * @since 2.30
 */
@Extension
public class UpdateCenterComponent extends Component {
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
        UpdateCenter uc = new UpdateCenter();
        Jenkins instance = Helper.getActiveInstance();
        hudson.model.UpdateCenter updateCenter = instance.getUpdateCenter();

        for (UpdateSite c : updateCenter.getSiteList()) {
            UpdateCenter.UpdateSite us = new UpdateCenter.UpdateSite();
            us.setUrl(c.getUrl());
            us.setConnectionCheckUrl(c.getConnectionCheckUrl());
            us.setImplementationName(c.getClass().getName());
            uc.addSitesList(us);
        }

        uc.setLastUpdatedString(updateCenter.getLastUpdatedString());

        // Only do this part of the async-http-client plugin is installed.
        if (instance.getPlugin("async-http-client") != null) {
            uc.setProxy(getProxyInformation());
        }

        container.add(new MarkdownContent("update-center.md", uc));
        container.add(new YamlContent("update-center.yaml", uc));
    }

    private UpdateCenter.Proxy getProxyInformation() {
        UpdateCenter.Proxy proxy = new UpdateCenter.Proxy();

        ProxyServer proxyServer = AHCUtils.getProxyServer();
        if (proxyServer != null) {
            proxy.setHost(proxyServer.getHost());
            proxy.setPort(proxyServer.getPort());

            for (String noHost : proxyServer.getNonProxyHosts()) {
                proxy.addNoProxyHost(noHost);
            }
        }
        return proxy;
    }
}
