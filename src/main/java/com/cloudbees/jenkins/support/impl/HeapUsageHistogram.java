package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Heap histogram from master node.
 */
@Extension
@Restricted(NoExternalUse.class)
public class HeapUsageHistogram extends Component {
    // first 200 classes so 203 lines required because of the header
    private static final int MAX = 203;

    // disabled by default because of JENKINS-49931
    private static /*final*/ boolean FULLGC = Boolean.getBoolean(HeapUsageHistogram.class.getCanonicalName() + ".FULLGC");

    private static final Logger logger = Logger.getLogger(HeapUsageHistogram.class.getName());

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
    public boolean isSelectedByDefault() {
        return false;
    }


    @Override
    public void addContents(@NonNull Container result) {
        result.add(
            new Content("nodes/master/heap-histogram.txt") {
                @Override
                public void writeTo(OutputStream os) throws IOException {
                    os.write(getLiveHistogram().getBytes("UTF-8"));
                }
            }
        );
    }

    private String getLiveHistogram() throws IOException {
        final String raw = getRawLiveHistogram();
        final String[] lines = raw.split("\n");
        final int limit = MAX <= lines.length ? MAX : lines.length;

        final StringBuilder bos = new StringBuilder();
        //starting in 1 because of an empty line
        for (int i=1; i<limit; i++) {
            bos.append(lines[i]).append('\n');
        }

        return bos.toString();
    }

    private String getRawLiveHistogram() {
        String result;
        try {
            ObjectName objName = new ObjectName("com.sun.management:type=DiagnosticCommand");
            MBeanServer platform = ManagementFactory.getPlatformMBeanServer();
            if (platform == null) {
                return "N/A";
            }
            String[] params = FULLGC ? null : new String[] {"-all"};

            result = (String) platform.invoke(objName, "gcClassHistogram", new Object[] {params}, new String[]{String[].class.getName()});
        }
        catch (InstanceNotFoundException | ReflectionException | MBeanException | MalformedObjectNameException e) {
            logger.log(Level.WARNING,"Could not record heap live histogram.", e);
            result = "N/A";
        }
        return result;
    }
}
