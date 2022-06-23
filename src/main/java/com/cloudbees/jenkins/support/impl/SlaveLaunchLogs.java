/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.LaunchLogsFileContent;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.cloudbees.jenkins.support.impl.JenkinsLogs.ROTATED_LOGFILE_FILTER;

/**
 * Adds agent launch logs, which captures the current and past running connections to the agent.
 */
@Extension
public class SlaveLaunchLogs extends ObjectComponent<Computer> {

    @DataBoundConstructor
    public SlaveLaunchLogs() {
        super();
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Agent Launch Logs";
    }

    @Override
    public void addContents(@NonNull Container container) {
        addAgentsLaunchLogs(container);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.Agent;
    }

    @Override
    public void addContents(@NonNull Container container, Computer item) {
        if (item.getNode() == null) {
            return;
        }
        File lastLog = item.getLogFile();
        if (lastLog.exists()) {
            Agent agent = new Agent(new File(Jenkins.get().getRootDir(), "logs/slaves/" + item.getName()), lastLog);
            if (!agent.isTooOld()) {
                addAgentLaunchLogs(container, agent);
            }
        }
    }
    
    /**
     * <p>
     * In the presence of {@link Cloud} plugins like EC2, we want to find past agents, not just current ones.
     * So we don't try to loop through {@link Node} here but just try to look at the file systems to find them
     * all.
     *
     * <p>
     * Generally these cloud plugins do not clean up old logs, so if run for a long time, the log directory
     * will be full of old files that are not very interesting. Use some heuristics to cut off logs
     * that are old.
     */
    private void addAgentsLaunchLogs(Container result) {

        List<Agent> all = new ArrayList<>();

        {// find all the agent launch log files and sort them newer ones first

            File agentLogsDir = new File(Jenkins.get().getRootDir(), "logs/slaves");
            File[] logs = agentLogsDir.listFiles();
            if (logs!=null) {
                for (File dir : logs) {
                    File lastLog = new File(dir, "slave.log");
                    if (lastLog.exists()) {
                        Agent s = new Agent(dir, lastLog);
                        if (s.isTooOld()) continue;   // we don't care
                        all.add(s);
                    }
                }
            }

            Collections.sort(all);
        }
        {// this might be still too many, so try to cap them.
            int acceptableSize = Math.max(256, Jenkins.get().getNodes().size() * 5);

            if (all.size() > acceptableSize)
                all = all.subList(0, acceptableSize);
        }

        // now add them all
        all.forEach(it -> addAgentLaunchLogs(result, it));
    }
    
    private void addAgentLaunchLogs(Container container, Agent agent) {
        File[] files = agent.dir.listFiles(ROTATED_LOGFILE_FILTER);
        if (files!=null) {
            for (File f : files) {
                container.add(new LaunchLogsFileContent("nodes/slave/{0}/launchLogs/{1}",
                    new String[]{agent.getName(), f.getName()}, f, FileListCapComponent.MAX_FILE_SIZE));
            }
        }
    }
    
    static class Agent implements Comparable<Agent> {
        /**
         * Launch log directory of the agent: logs/slaves/NAME
         */
        final File dir;
        final long time;

        Agent(File dir, File lastLog) {
            this.dir = dir;
            this.time = lastLog.lastModified();
        }

        /** Agent name */
        String getName() { return dir.getName(); }

        /**
         * Use the primary log file's timestamp to compare newer agents from older agents.
         *
         * sort in descending order; newer ones first.
         */
        public int compareTo(Agent that) {
            return Long.compare(that.time, this.time);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Agent agent = (Agent) o;

            return time == agent.time;
        }

        @Override
        public int hashCode() {
            return (int) (time ^ (time >>> 32));
        }

        /**
         * If the file is more than 7 days old, it is considered too old.
         */
        public boolean isTooOld() {
            return time < System.currentTimeMillis()- TimeUnit.DAYS.toMillis(7);
        }
    }
    
    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public boolean isSelectedByDefault(Computer item) {
        return true;
    }

    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz) || Computer.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean isApplicable(Computer item) {
        return item != Jenkins.get().toComputer();
    }

    @Override
    public SlaveLaunchLogs.DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(SlaveLaunchLogs.DescriptorImpl.class);
    }

    @Extension
    @Symbol("agentsLaunchLogsComponent")
    public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Agent Launch Logs";
        }

    }
}
