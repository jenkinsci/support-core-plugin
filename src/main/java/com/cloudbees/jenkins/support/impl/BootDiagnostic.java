package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Collections;
import java.util.Set;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class BootDiagnostic extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Boot Diagnostic";
    }

    public void addContents(@NonNull Container result) {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) return; // TODO Jenkins.getActiveInstance()
        File f = new File(jenkins.getRootDir(), "logs/boot-metrics.log");
        if (f.exists()) {
            result.add(new FileContent("other-logs/" + f.getName(), f));
        }
    }
}
