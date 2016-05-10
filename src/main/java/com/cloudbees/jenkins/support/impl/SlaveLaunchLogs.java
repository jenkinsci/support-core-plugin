package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.util.Helper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Node;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.cloudbees.jenkins.support.impl.JenkinsLogs.ROTATED_LOGFILE_FILTER;

/**
 * Adds slave launch logs, which captures the current and past running connections to the slave.
 *
 */
public class SlaveLaunchLogs extends Component{

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Slave Launch Logs";
    }

    @Override
    public void addContents(@NonNull Container container) {
        addSlaveLaunchLog(container);
    }

    /**
     *
     * <p>
     * In the presence of {@link Cloud} plugins like EC2, we want to find past slaves, not just current ones.
     * So we don't try to loop through {@link Node} here but just try to look at the file systems to find them
     * all.
     *
     * <p>
     * Generally these cloud plugins do not clean up old logs, so if run for a long time, the log directory
     * will be full of old files that are not very interesting. Use some heuristics to cut off logs
     * that are old.
     */
    private void addSlaveLaunchLog(Container result) {
        class Slave implements Comparable<Slave> {
            /**
             * Launch log directory of the slave: logs/slaves/NAME
             */
            File dir;
            long time;

            Slave(File dir, File lastLog) {
                this.dir = dir;
                this.time = lastLog.lastModified();
            }

            /** Slave name */
            String getName() { return dir.getName(); }

            /**
             * Use the primary log file's timestamp to compare newer slaves from older slaves.
             *
             * sort in descending order; newer ones first.
             */
            public int compareTo(Slave that) {
                long lhs = this.time;
                long rhs = that.time;
                if (lhs<rhs)    return 1;
                if (lhs>rhs)    return -1;
                return 0;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Slave slave = (Slave) o;

                if (time != slave.time) return false;

                return true;
            }

            @Override
            public int hashCode() {
                return (int) (time ^ (time >>> 32));
            }

            /**
             * If the file is more than a year old, can't imagine how that'd be of any interest.
             */
            public boolean isTooOld() {
                return time < System.currentTimeMillis()- TimeUnit.DAYS.toMillis(365);
            }
        }

        List<Slave> all = new ArrayList<Slave>();

        {// find all the slave launch log files and sort them newer ones first

            File slaveLogsDir = new File(Helper.getActiveInstance().getRootDir(), "logs/slaves");
            File[] logs = slaveLogsDir.listFiles();
            if (logs!=null) {
                for (File dir : logs) {
                    File lastLog = new File(dir, "slave.log");
                    if (lastLog.exists()) {
                        Slave s = new Slave(dir, lastLog);
                        if (s.isTooOld()) continue;   // we don't care
                        all.add(s);
                    }
                }
            }

            Collections.sort(all);
        }

        {// this might be still too many, so try to cap them.
            int acceptableSize = Math.max(256, Helper.getActiveInstance().getNodes().size() * 5);

            if (all.size() > acceptableSize)
                all = all.subList(0, acceptableSize);
        }

        // now add them all
        for (Slave s : all) {
            File[] files = s.dir.listFiles(ROTATED_LOGFILE_FILTER);
            if (files!=null)
                for (File f : files) {
                    result.add(new FileContent("nodes/slave/" + s.getName() + "/launchLogs/"+f.getName() , f));
                }
        }
    }
}
