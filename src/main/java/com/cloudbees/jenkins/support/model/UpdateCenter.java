package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/21/17.
 */
@Data
public class UpdateCenter implements Serializable, MarkdownFile {
    String lastUpdatedString;
    List<UpdateSite> sitesList = new ArrayList<UpdateSite>();
    Proxy proxy;

    public void addSitesList(UpdateSite us) {
        sitesList.add(us);
    }

    @Data public static class Proxy {
        String host;
        int port;
        List<String> noProxyHosts = new ArrayList<>();

        public void addNoProxyHost(String noProxyHost) {
            noProxyHosts.add(noProxyHost);
        }
    }

    @Data
    public static class UpdateSite {
        String url;
        String connectionCheckUrl;
        String implementationName;
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
