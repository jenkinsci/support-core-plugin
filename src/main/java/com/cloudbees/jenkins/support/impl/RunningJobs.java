package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class RunningJobs extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "Running Jobs";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(
            new PrefilteredPrintedContent("nodes/master/running-jobs.txt") {
                @Override
                protected void printTo(PrintWriter out, ContentFilter filter) {
                    Optional.ofNullable(Jenkins.getInstanceOrNull())
                        .ifPresent(jenkins -> Arrays.stream(jenkins.getComputers())
                            .flatMap(computer -> computer.getAllExecutors().stream())
                            .collect(Collectors.toList())
                            .forEach(executor -> Optional.ofNullable(executor.getCurrentExecutable())
                                .filter(executable -> !(executable.getParent() instanceof ExecutorStepExecution.PlaceholderTask))
                                .ifPresent(executable -> out.println(ContentFilter.filter(filter, executable.toString())))
                            )
                        );
                }
            });
    }
}
