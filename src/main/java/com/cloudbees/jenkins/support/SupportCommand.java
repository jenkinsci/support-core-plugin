/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.impl.JVMProcessSystemMetricsContents;
import com.cloudbees.jenkins.support.impl.SystemConfiguration;
import hudson.CloseProofOutputStream;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.remoting.RemoteOutputStream;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.kohsuke.args4j.Argument;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Extension
public class SupportCommand extends CLICommand {

    @Argument(metaVar = "COMPONENTS")
    public List<String> components = new ArrayList<>();

    @Override
    public String getShortDescription() {
        return Messages.SupportCommand_generates_a_diagnostic_support_bundle_();
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(Messages.SupportCommand_if_no_arguments_are_given_generate_a_bun());
        int maxlen = 0;
        for (Component c : SupportPlugin.getComponents()) {
            maxlen = Math.max(maxlen, c.getId().length());
        }
        for (Component c : SupportPlugin.getComponents()) {
            stderr.printf("%-" + maxlen + "s %s%n", c.getId(), c.getDisplayName());
        }
    }

    @Override
    protected int run() throws Exception {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        Set<Component> selected = new HashSet<>();
        
        // JENKINS-63722: If "Master" or "Agents" are unselected, show a warning and add the components to the list 
        // for backward compatibility
        if(components.contains("Master")) {
            stderr.println("WARNING:" + Messages._SupportCommand_jenkins_63722_deprecated_ids("Master"));
            selected.addAll(Jenkins.get().getExtensionList(JVMProcessSystemMetricsContents.Master.class));
            selected.addAll(Jenkins.get().getExtensionList(SystemConfiguration.Master.class));
        } else if (components.contains("Agents")) {
            stderr.println("WARNING:" + Messages._SupportCommand_jenkins_63722_deprecated_ids("Agents"));
            selected.addAll(Jenkins.get().getExtensionList(JVMProcessSystemMetricsContents.Agents.class));
            selected.addAll(Jenkins.get().getExtensionList(SystemConfiguration.Agents.class));
        }
        
        for (Component c : SupportPlugin.getComponents()) {
            if (c.isEnabled() && (components.isEmpty() || components.contains(c.getId()))) {
                selected.add(c);
            }
        }
        SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication2());
        try {
            try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                OutputStream os;
                if (channel != null) { // Remoting mode
                    os = channel.call(new SaveBundle(BundleFileName.generate()));
                } else { // redirect output to a ZIP file yourself
                    os = new CloseProofOutputStream(stdout);
                }
                SupportPlugin.writeBundle(os, new ArrayList<>(selected));
            }
        } finally {
            SupportPlugin.clearRequesterAuthentication();
        }
        return 0;
    }

    private static class SaveBundle extends MasterToSlaveCallable<OutputStream, IOException> {
        private final String filename;

        SaveBundle(String filename) {
            this.filename = filename;
        }

        @Override
        public OutputStream call() throws IOException {
            Path path = Files.createFile(Paths.get(System.getProperty("java.io.tmpdir"), filename));
            System.err.println("Creating: " + path);
            return new RemoteOutputStream(new FileOutputStream(path.toFile()));
        }
    }

}
