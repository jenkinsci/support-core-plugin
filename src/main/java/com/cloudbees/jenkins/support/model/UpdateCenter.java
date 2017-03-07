package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/21/17.
 */

public class UpdateCenter implements Serializable, MarkdownFile {
    String lastUpdatedString;
    List<UpdateSite> sitesList = new ArrayList<UpdateSite>();
    Proxy proxy;

    public void addSitesList(UpdateSite us) {
        sitesList.add(us);
    }

    public String getLastUpdatedString() {
        return lastUpdatedString;
    }

    public void setLastUpdatedString(String lastUpdatedString) {
        this.lastUpdatedString = lastUpdatedString;
    }

    public List<UpdateSite> getSitesList() {
        return sitesList;
    }

    public void setSitesList(List<UpdateSite> sitesList) {
        this.sitesList = sitesList;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public static class Proxy implements Serializable {
        String host;
        int port;
        List<String> noProxyHosts = new ArrayList<>();

        public void addNoProxyHost(String noProxyHost) {
            noProxyHosts.add(noProxyHost);
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public List<String> getNoProxyHosts() {
            return noProxyHosts;
        }

        public void setNoProxyHosts(List<String> noProxyHosts) {
            this.noProxyHosts = noProxyHosts;
        }
    }


    public static class UpdateSite implements Serializable {
        String url;
        String connectionCheckUrl;
        String implementationName;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getConnectionCheckUrl() {
            return connectionCheckUrl;
        }

        public void setConnectionCheckUrl(String connectionCheckUrl) {
            this.connectionCheckUrl = connectionCheckUrl;
        }

        public String getImplementationName() {
            return implementationName;
        }

        public void setImplementationName(String implementationName) {
            this.implementationName = implementationName;
        }
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("=== Sites ===");

        for (UpdateSite c : getSitesList()) {
            out.println(" - Url: " + c.getUrl());
            out.println(" - Connection Url: " + c.getConnectionCheckUrl());
            out.println(" - Implementation Type: " + c.getImplementationName());
        }

        out.println("======");
        out.println("Last updated: " + lastUpdatedString);

        if (proxy != null) {
            out.println("=== Proxy ===");
            out.println(" - Host: " + proxy.getHost());
            out.println(" - Port: " + proxy.getPort());

            out.println(" - No Proxy Hosts: ");
            for (String noHost : proxy.getNoProxyHosts()) {
                out.println(" * " + noHost);
            }
        } else {
            out.println("Proxy: 'async-http-client' not installed, so no proxy info available.");
        }
    }
}
