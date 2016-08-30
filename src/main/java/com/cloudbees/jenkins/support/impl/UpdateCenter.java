package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import jenkins.model.Jenkins;

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
                                out.println(" - Implementation Type: " + c.getClass().getName());
                            }

                            out.println("======");

                            out.println("Last updated: " + updateCenter.getLastUpdatedString());
                        }
                    }
                }
        );
    }
}
