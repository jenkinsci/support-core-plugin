package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.util.Markdown;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;

/**
 * Add information about the Jenkins proxy configuration.
 */
@Extension
public class ProxyConfiguration extends Component {
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Proxy Configuration";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrefilteredPrintedContent("proxy.md") {

            @Override
            public void printTo(PrintWriter out, @NonNull ContentFilter filter) {
                out.println("Proxy");
                out.println("===============");
                out.println();
                hudson.ProxyConfiguration proxy = Jenkins.get().getProxy();
                if (proxy != null) {
                    out.println("  - Host: `" + Markdown.escapeBacktick(ContentFilter.filter(filter, proxy.getName()))
                            + "`");
                    out.println("  - Port: `" + proxy.getPort() + "`");
                    out.println("  - No Proxy Hosts: ");
                    String noProxyHostsString = proxy.getNoProxyHost();
                    if (noProxyHostsString != null) {
                        Arrays.stream(noProxyHostsString.split("[ \t\n,|]+"))
                                .forEach(noProxyHost -> out.println("      * `"
                                        + Markdown.escapeBacktick(ContentFilter.filter(filter, noProxyHost)) + "`"));
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    @Override
    public SupportAction.PreChooseOptions[] getDefautlPreChooseOptions() {
        return new SupportAction.PreChooseOptions[]{ SupportAction.PreChooseOptions.Default, SupportAction.PreChooseOptions.ConfigurationFiles, SupportAction.PreChooseOptions.PerformanceData };
    }
}
