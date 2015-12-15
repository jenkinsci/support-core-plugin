package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.StringContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.*;
import java.util.Collections;
import java.util.Set;

/**
 * @since 2.30
 * @author schristou88
 *         Date: 11/25/15
 *         Time: 5:36 PM
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
        container.add(
                new Content("update-center.md") {
                    @Override
                    public void writeTo(OutputStream os) throws IOException {
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));

                        Jenkins instance = Jenkins.getInstance();
                        if (instance == null) {
                            out.println("Jenkins has not started yet. No update center information is available.");
                        } else {
                            hudson.model.UpdateCenter updateCenter = instance.getUpdateCenter();
                            out.println("=== Sites ===");
                            for (UpdateSite c : updateCenter.getSiteList()) {
                                out.println(" - Url: " + c.getUrl());
                                out.println(" - Connection Url: " + c.getConnectionCheckUrl());
                            }

                            out.println("======");

                            out.println("Update center name: " + updateCenter.getClass().getName());
                            out.println("Last updated: " + updateCenter.getLastUpdatedString());
                        }
                    }
                }
        );
    }
}
