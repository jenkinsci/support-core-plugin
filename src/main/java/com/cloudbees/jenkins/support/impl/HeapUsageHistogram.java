package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Heap histogram from master node.
 */
@Extension
@Restricted(NoExternalUse.class)
public class HeapUsageHistogram extends Component {
    private static final int OFFSET = 3;
    private static final int MAX = 200 + OFFSET;

    private static final Logger logger = Logger.getLogger(HeapUsageHistogram.class.getName());
    private final WeakHashMap<Node, String> heapHistoCache = new WeakHashMap<Node, String>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Master Heap Histogram";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(
            new Content("nodes/master/heap-histogram.txt") {
                @Override
                public void writeTo(OutputStream os) throws IOException {
                    os.write(getLiveHistogram(Jenkins.getInstance()).getBytes("UTF-8"));
                }
            }
        );
    }

    private String getLiveHistogram(Node node) throws IOException {
        return AsyncResultCache.get(node,
                heapHistoCache,
                new GetLiveHeapHistogram(),
                "heap histogram",
                "N/A: No connection to node, or no cache.");
    }

    private static final class GetLiveHeapHistogram implements Callable<String, RuntimeException> {
        public String call() {
            final String raw = getRawLiveHistogram();
            final String[] lines = raw.split("\n");
            final int limit = MAX <= lines.length ? MAX : lines.length;

            final StringBuilder bos = new StringBuilder();

            bos.append("Master Heap Histogram");
            for (int i=0; i<limit; i++) {
                bos.append(lines[i]).append('\n');
            }

            return bos.toString();
        }

        /** {@inheritDoc} */
        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // TODO: do we have to verify some role?
        }

        private String getRawLiveHistogram() {
            String result;
            try {
                ObjectName objName = new ObjectName("com.sun.management:type=DiagnosticCommand");
                MBeanServer platform = ManagementFactory.getPlatformMBeanServer();
                if (platform == null) {
                    return "N/A";
                }
                result = (String) platform.invoke(objName, "gcClassHistogram", new Object[] {null}, new String[]{String[].class.getName()});
            }
            catch (InstanceNotFoundException | ReflectionException | MBeanException | MalformedObjectNameException e) {
                logger.log(Level.WARNING,"Could not record heap live histogram.", e);
                result = "N/A";
            }
            return result;
        }
    }
}
