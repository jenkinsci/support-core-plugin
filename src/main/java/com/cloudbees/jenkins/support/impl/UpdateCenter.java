package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UpdateSite;
import hudson.security.Permission;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;

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
    public int getHash() {
        return 47;
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrefilteredPrintedContent("update-center.md") {
            @Override
            public void printTo(PrintWriter out, @NonNull ContentFilter filter) {
                hudson.model.UpdateCenter updateCenter = Jenkins.get().getUpdateCenter();
                out.println("Sites");
                out.println("===============");
                out.println();
                for (UpdateSite c : updateCenter.getSiteList()) {
                    out.printf("  - Id: %s%n", c.getId());
                    out.println("  - Url: " + ContentFilter.filter(filter, c.getUrl()));
                    out.println("  - Connection Url: " + ContentFilter.filter(filter, c.getConnectionCheckUrl()));
                    out.println("  - Implementation Type: " + c.getClass().getName());
                }

                out.println();
                out.println("Refresh");
                out.println("===============");
                out.println();
                out.println("  - Last updated: " + updateCenter.getLastUpdatedString());
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }
}
