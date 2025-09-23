/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.util.Markdown;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.diagnosis.OldDataMonitor;
import hudson.diagnosis.ReverseProxySetupMonitor;
import hudson.model.AdministrativeMonitor;
import hudson.model.Saveable;
import hudson.security.Permission;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;

/**
 * Warns if any administrative monitors are currently active.
 */
@Extension
public final class AdministrativeMonitors extends Component {

    @NonNull
    @Override
    public String getDisplayName() {
        return "Administrative monitors";
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(Container result) {
        result.add(new PrefilteredPrintedContent("admin-monitors.md") {

            @Override
            protected void printTo(PrintWriter out, @NonNull ContentFilter filter) throws IOException {
                out.println("Monitors");
                out.println("========");
                AdministrativeMonitor.all().stream()
                        .filter(monitor -> !(monitor instanceof ReverseProxySetupMonitor)
                                && monitor.isEnabled()
                                && monitor.isActivated())
                        .sorted(Comparator.comparing(o -> o.id))
                        .forEach(monitor -> {
                            out.println();
                            out.println("`" + monitor.id + "` _" + Markdown.escapeUnderscore(monitor.getDisplayName())
                                    + "_");
                            out.println("--------------");
                            if (monitor instanceof OldDataMonitor odm) {
                                for (Map.Entry<Saveable, OldDataMonitor.VersionRange> entry :
                                        odm.getData().entrySet()) {
                                    out.println("  * Problematic object: `"
                                            + filter.filter(entry.getKey().toString()) + "`");
                                    OldDataMonitor.VersionRange value = entry.getValue();
                                    String range = value.toString();
                                    if (!range.isEmpty()) {
                                        out.println("    - " + range);
                                    }
                                    String extra = value.extra;
                                    if (extra != null && !extra.isBlank()) {
                                        out.println("    - "
                                                + filter.filter(
                                                        extra)); // TODO could be a multiline stack trace, quote it
                                    }
                                }
                            } else {
                                // No specific content we can show; message.jelly is for HTML only.
                                out.println("(active and enabled)");
                            }
                        });
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

}
