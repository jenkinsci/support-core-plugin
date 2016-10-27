package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;

import java.util.Collections;
import java.util.Set;

/**
 * Created by michaelneale on 24/10/16.
 */
@Extension
public class BrowserErrors extends Component {


    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions()  {
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Browser console errors";
    }

    @Override
    public void addContents(@NonNull Container container) {
        return;

    }
}
