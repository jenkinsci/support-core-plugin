package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;

/**
 * Environment variables on the nodes.
 *
 * @author Stephen Connolly
 */
@Extension
public class EnvironmentVariables extends Component {

    private static final Map<String, String> UNAVAILABLE = Collections.singletonMap("N/A", "N/A");
    private final Logger logger = Logger.getLogger(EnvironmentVariables.class.getName());

    private final WeakHashMap<Node, Map<String,String>> environmentVariableCache = new WeakHashMap<Node, Map<String, String>>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "Environment variables";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new PrintedContent("nodes/master/environment.txt") {
                    @Override
                    protected void printTo(PrintWriter out) throws IOException {
                        try {
                            for (Map.Entry<String, String> entry : getEnvironmentVariables(Jenkins.getInstance()).entrySet()) {
                                out.println(entry.getKey() + "=" + entry.getValue());
                            }
                        } catch (IOException e) {
                            logger.log(Level.WARNING, "Could not record environment of controller", e);
                        }
                    }
                }
        );
        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(
                    new PrintedContent("nodes/slave/{0}/environment.txt", node.getNodeName()) {
                        @Override
                        protected void printTo(PrintWriter out) throws IOException {
                            try {
                                for (Map.Entry<String, String> entry : getEnvironmentVariables(node)
                                        .entrySet()) {
                                    out.println(entry.getKey() + "=" + entry.getValue());
                                }
                            } catch (IOException e) {
                                logger.log(Level.WARNING,
                                        "Could not record environment of node " + node.getNodeName(), e);
                            }
                        }
                    }
            );
        }
    }

    public Map<String,String> getEnvironmentVariables(Node node) throws IOException {
        return AsyncResultCache.get(node, environmentVariableCache, new GetEnvironmentVariables(), "environment",
                UNAVAILABLE);
    }

    @Deprecated
    public static Map<String, String> getEnvironmentVariables(VirtualChannel channel)
            throws IOException, InterruptedException {
        if (channel == null) {
            return Collections.singletonMap("N/A", "N/A");
        }
        return channel.call(new GetEnvironmentVariables());
    }

    private static final class GetEnvironmentVariables extends MasterToSlaveCallable<Map<String, String>, RuntimeException> {
        public Map<String, String> call() {
            return new TreeMap<String, String>(AccessController.doPrivileged(
                    new PrivilegedAction<Map<String, String>>() {
                        public Map<String, String> run() {
                            return System.getenv();
                        }
                    }));
        }

        private static final long serialVersionUID = 1L;
    }

}
