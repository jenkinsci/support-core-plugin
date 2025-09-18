package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;

@Extension
public class RunningBuilds extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "Running Builds";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new PrefilteredPrintedContent("running-builds.txt") {
            @Override
            protected void printTo(PrintWriter out, ContentFilter filter) {
                Arrays.stream(Jenkins.get().getComputers())
                        .flatMap(computer -> computer.getAllExecutors().stream())
                        .collect(Collectors.toList())
                        .forEach(executor -> Optional.ofNullable(executor.getCurrentExecutable())
                                .filter(executable -> executable.getParent()
                                        == executable.getParent().getOwnerTask())
                                .ifPresent(executable ->
                                        out.println(ContentFilter.filter(filter, executable.toString()))));
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.BUILDS;
    }

    @Override
    public SupportAction.PreChooseOptions[] getDefautlPreChooseOptions() {
        return new SupportAction.PreChooseOptions[]{ SupportAction.PreChooseOptions.Default, SupportAction.PreChooseOptions.ConfigurationFiles, SupportAction.PreChooseOptions.PerformanceData };
    }
}
