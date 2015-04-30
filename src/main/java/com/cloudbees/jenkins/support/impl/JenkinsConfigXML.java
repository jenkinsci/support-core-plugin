package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

/**
 * Created by josh on 4/30/15.
 */
@Extension
public class JenkinsConfigXML extends Component{
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Jenkins root config.xml file";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new FileContent("nodes/master/config.xml", new File(Jenkins.getInstance().getRootDir(), "config.xml")));
    }
}
