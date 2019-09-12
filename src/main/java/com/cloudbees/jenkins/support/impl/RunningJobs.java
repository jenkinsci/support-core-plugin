package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

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
                    try (PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)))) {
                        Optional.ofNullable(Jenkins.getInstanceOrNull())
                            .ifPresent(jenkins -> jenkins.getAllItems(Job.class)
                                .stream()
                                .filter(Job::isBuilding)
                                .forEach(job -> {
                                    out.println(job.getFullName());
                                }));

                    } finally {
                        os.flush();
                    }
                }
            }
        );
    }


}
