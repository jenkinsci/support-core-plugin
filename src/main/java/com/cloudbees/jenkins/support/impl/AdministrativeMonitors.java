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

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import hudson.Extension;
import hudson.diagnosis.OldDataMonitor;
import hudson.diagnosis.ReverseProxySetupMonitor;
import hudson.model.AdministrativeMonitor;
import hudson.model.Saveable;
import hudson.security.Permission;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

/**
 * Warns if any administrative monitors are currently active.
 */
@Extension public final class AdministrativeMonitors extends Component {

    @Override public String getDisplayName() {
        return "Administrative monitors";
    }

    @Override public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override public void addContents(Container result) {
        final Map<String,AdministrativeMonitor> activated = new TreeMap<String,AdministrativeMonitor>();
        for (AdministrativeMonitor monitor : AdministrativeMonitor.all()) {
            if (monitor instanceof ReverseProxySetupMonitor) {
                // This one is pretty special: always activated, but may or may not show anything in message.jelly.
                continue;
            }
            if (monitor.isActivated() && monitor.isEnabled()) {
                activated.put(monitor.id, monitor);
            }
            // disabled monitors could include RekeySecretAdminMonitor; no reason to show it
        }
        if (!activated.isEmpty()) {
            result.add(new PrintedContent("admin-monitors.md") {
                @Override protected void printTo(PrintWriter out) throws IOException {
                    out.println("Monitors");
                    out.println("========");
                    for (AdministrativeMonitor monitor : activated.values()) {
                        out.println();
                        out.println("`" + monitor.id + "`");
                        out.println("--------------");
                        if (monitor instanceof OldDataMonitor && !suffersFromJENKINS24358()) {
                            OldDataMonitor odm = (OldDataMonitor) monitor;
                            for (Map.Entry<Saveable,OldDataMonitor.VersionRange> entry : odm.getData().entrySet()) {
                                out.println("  * Problematic object: `" + entry.getKey() + "`");
                                OldDataMonitor.VersionRange value = entry.getValue();
                                String range = value.toString();
                                if (!range.isEmpty()) {
                                    out.println("    - " + range);
                                }
                                String extra = value.extra;
                                if (!StringUtils.isBlank(extra)) {
                                    out.println("    - " + extra);
                                }
                            }
                        } else {
                            // No specific content we can show; message.jelly is for HTML only.
                            out.println("(active and enabled)");
                        }
                    }
                }
            });
        }
    }

    private static boolean suffersFromJENKINS24358() {
        VersionNumber version = Jenkins.getVersion();
        if (version == null) {
            return false;
        } else if (version.compareTo(/*JENKINS-19544*/new VersionNumber("1.557")) >=0 && version.compareTo(new VersionNumber(/*JENKINS-24358*/"1.578")) < 0) {
            if (version.toString().startsWith("1.565.") && version.compareTo(new VersionNumber(/* predicting JENKINS-24358 to be backported here */"1.565.3")) >=0) {
                return false;
            } else {
                return true;
            }
        } else if (version.toString().startsWith(/* JENKINS-19544 backported to 1.554.1 */"1.554.")) {
            return true;
        } else { // before regression or after fix
            return false;
        }
    }
}
