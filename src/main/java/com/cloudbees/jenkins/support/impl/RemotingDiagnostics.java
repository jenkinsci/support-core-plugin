package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.remoting.Channel;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

/**
 * Captures diagnostics information from remoting channels.
 *
 * @author Kohsuke Kawaguchi
 */
public class RemotingDiagnostics extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Remoting Channel Diagnostics";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrintedContent("channel-diagnostics.md") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                // this method is new in remoting. see JENKINS-39150 change in remoting
                try {
                    Method m = Channel.class.getMethod("dumpDiagnosticsForAll", PrintWriter.class);
                    m.invoke(null,out);
                } catch (Exception e) {
                    Functions.printStackTrace(e, out);
                }
            }

            @Override
            public boolean shouldBeFiltered() {
                // The information of this content is not sensible, so it doesn't need to be filtered.
                return false;
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.AGENT;
    }
}
