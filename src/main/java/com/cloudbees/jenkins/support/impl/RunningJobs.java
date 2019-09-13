package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Executor;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
            new Content("nodes/master/running-jobs.txt") {
                @Override
                public void writeTo(OutputStream os) throws IOException {
                    try (OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8); BufferedWriter bw = new BufferedWriter(osw); PrintWriter out = new PrintWriter(bw)) {
                        Optional.ofNullable(Jenkins.getInstanceOrNull())
                            .ifPresent(jenkins -> Arrays.stream(jenkins.getComputers())
                                .flatMap(computer -> computer.getExecutors().stream())
                                .filter(Executor::isBusy)
                                .collect(Collectors.toList())
                                .forEach(executor -> Optional.ofNullable(executor.getCurrentExecutable())
                                    .ifPresent(executable -> out.println(executable.getParent().getOwnerTask().getUrl()))));
                    }
                }
            }
        );
    }
}
