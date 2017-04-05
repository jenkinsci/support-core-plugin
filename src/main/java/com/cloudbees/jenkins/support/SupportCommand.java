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
import hudson.CloseProofOutputStream;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.remoting.RemoteOutputStream;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.args4j.Argument;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Extension
public class SupportCommand extends CLICommand {

    @Argument(metaVar = "COMPONENTS")
    public List<String> components = new ArrayList<String>();

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
        Jenkins.getInstance().checkPermission(SupportPlugin.CREATE_BUNDLE);
        List<Component> selected = new ArrayList<Component>();
        for (Component c : SupportPlugin.getComponents()) {
            if (c.isEnabled() && (components.isEmpty() || components.contains(c.getId()))) {
                selected.add(c);
            }
        }
        SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication());
        try {
            SecurityContext old = ACL.impersonate(ACL.SYSTEM);
            try {
                OutputStream os;
                if (channel != null) { // Remoting mode
                    os = channel.call(new SaveBundle(SupportPlugin.getBundleFileName()));
                } else { // redirect output to a ZIP file yourself
                    os = new CloseProofOutputStream(stdout);
                }
                SupportPlugin.writeBundle(os, selected);
            } finally {
                SecurityContextHolder.setContext(old);
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
