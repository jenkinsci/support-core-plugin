/*
 * The MIT License
 *
 * Copyright (c) 2015 schristou88
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
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Queue;
import hudson.model.queue.WorkUnit;
import hudson.security.Permission;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.CauseOfInterruption;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Gather information about the node executors.
 */
@Extension
public class NodeExecutors extends ObjectComponent<Computer> {

    @DataBoundConstructor
    public NodeExecutors() {
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
        return "Node Executors";
    }

    @Override
    public int getHash() {
        return 24;
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrefilteredPrintedContent("executors.md") {
            @Override
            public void printTo(PrintWriter out, ContentFilter filter) {
                try {
                    out.println("Node Executors");
                    out.println("===========");
                    out.println();

                    Arrays.stream(Jenkins.get().getComputers()).forEach(computer -> {
                        dumpExecutorInfo(computer, out, filter);
                    });

                } finally {
                    out.flush();
                }
            }
        });
    }

    @Override
    public void addContents(@NonNull Container container, @NonNull Computer computer) {
        container.add(new PrefilteredPrintedContent("executors.md") {
            @Override
            public void printTo(PrintWriter out, ContentFilter filter) {
                try {
                    out.println("Node Executors");
                    out.println("===========");
                    out.println();

                    dumpExecutorInfo(computer, out, filter);

                } finally {
                    out.flush();
                }
            }
        });
    }

    private void dumpExecutorInfo(@CheckForNull Computer computer, PrintWriter out, ContentFilter filter) {
        if (computer != null) {
            out.println("  * " + ContentFilter.filter(filter, computer.getDisplayName()));
            computer.getAllExecutors().forEach(executor -> {
                out.println("      - " + ContentFilter.filter(filter, executor.getDisplayName()));
                out.println("          - active: " + executor.isActive());
                out.println("          - busy: " + executor.isBusy());
                out.println("          - causesOfInterruption: ["
                        + ContentFilter.filter(
                                filter,
                                executor.getCausesOfInterruption().stream()
                                        .map(CauseOfInterruption::getShortDescription)
                                        .collect(Collectors.joining(",")))
                        + "]");
                out.println("          - idle: " + executor.isIdle());
                long idleStartMilliseconds = executor.getIdleStartMilliseconds();
                out.println("          - idleStartMilliseconds: " + idleStartMilliseconds + " ("
                        + Util.XS_DATETIME_FORMATTER2.format(Instant.ofEpochMilli(idleStartMilliseconds)) + ")");
                out.println("          - progress: " + executor.getProgress());
                out.println("          - state: " + executor.getState());
                WorkUnit workUnit = executor.getCurrentWorkUnit();
                if (workUnit != null) {
                    out.println("          - currentWorkUnit: " + ContentFilter.filter(filter, workUnit.toString()));
                }
                Queue.Executable executable = executor.getCurrentExecutable();
                if (executable != null) {
                    out.println("          - executable: " + ContentFilter.filter(filter, executable.toString()));
                    out.println("          - elapsedTime: " + executor.getTimestampString());
                }
            });
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.AGENT;
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
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
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("nodeExecutors")
    public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Node Executors";
        }
    }
}
