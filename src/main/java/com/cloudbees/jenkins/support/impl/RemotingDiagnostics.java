package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.ContentData;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
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
        addContents(container, false);
    }

    @Override
    public void addContents(@NonNull Container container, boolean shouldAnonymize) {
        container.add(new PrintedContent(new ContentData("channel-diagnostics.md", shouldAnonymize)) {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                // this method is new in remoting. see JENKINS-39150 change in remoting
                try {
                    Method m = Channel.class.getMethod("dumpDiagnosticsForAll", PrintWriter.class);
                    m.invoke(null,out);
                } catch (Exception e) {
                    SupportLogFormatter.printStackTrace(e, out);
                }
            }
        });
    }
}
