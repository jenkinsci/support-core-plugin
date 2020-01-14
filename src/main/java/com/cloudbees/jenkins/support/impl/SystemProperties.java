package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.util.RemotingDiagnostics;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;

/**
 * JVM System properties from the nodes.
 *
 * @author Stephen Connolly
 */
@Extension
public class SystemProperties extends Component {

    private static final Map<Object, Object> UNAVAILABLE = Collections.<Object, Object>singletonMap("N/A", "N/A");
    private final Logger logger = Logger.getLogger(SystemProperties.class.getName());
    private final WeakHashMap<Node, Map<Object,Object>> systemPropertyCache = new WeakHashMap<Node, Map<Object, Object>>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "System properties";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new Content("nodes/master/system.properties") {
                       @Override
                       public void writeTo(OutputStream os) {
                           try {
                               Properties properties = new SortedProperties();
                               properties.putAll(RemotingDiagnostics
                                       .getSystemProperties(Jenkins.getInstance().getChannel()));
                               properties.store(os, null);
                           } catch (IOException e) {
                               logger.log(Level.WARNING, "Could not record system properties for master", e);
                           } catch (InterruptedException e) {
                               logger.log(Level.WARNING, "Could not record system properties for master", e);
                           }
                       }
                   }
        );
        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(
                    new Content("nodes/slave/{0}/system.properties", node.getNodeName()) {
                        @Override
                        public void writeTo(OutputStream os) {
                            try {
                                Properties properties = new SortedProperties();
                                properties.putAll(getSystemProperties(node));
                                properties.store(os, null);
                            } catch (IOException e) {
                                logger.log(Level.WARNING, "Could not record system properties for " + node.getNodeName(), e);
                            }
                        }
                    }
            );
        }
    }

    public Map<Object, Object> getSystemProperties(Node node) throws IOException  {
        return AsyncResultCache.get(node, systemPropertyCache, new GetSystemProperties(), "system properties", UNAVAILABLE);
    }

    @Deprecated
    public static Map<Object, Object> getSystemProperties(VirtualChannel channel)
            throws IOException, InterruptedException {
        if (channel == null) {
            return UNAVAILABLE;
        }
        return channel.call(new GetSystemProperties());
    }

    private static final class GetSystemProperties extends MasterToSlaveCallable<Map<Object, Object>, RuntimeException> {
        public Map<Object, Object> call() {
            return new TreeMap<Object, Object>(AccessController.doPrivileged(new PrivilegedAction<Properties>() {
                public Properties run() {
                    return System.getProperties();
                }
            }));
        }

        private static final long serialVersionUID = 1L;
    }

    private static class SortedProperties extends Properties {
        @Override
        public synchronized Enumeration<Object> keys() {
            Enumeration keysEnum = super.keys();
            Vector keyList = new Vector();
            while (keysEnum.hasMoreElements()) {
                keyList.add(keysEnum.nextElement());
            }
            Collections.sort(keyList);
            return keyList.elements();
        }
    }
}
