package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.StringContent;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contributes slow request reports into the support bundle.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestComponent extends RequestComponent {
    @Inject
    SlowRequestChecker checker;

    @NonNull
    @Override
    public String getDisplayName() {
        return "Slow Request Records";
    }

    @Override
    public void addContents(@NonNull Container container) {
        super.addContents(container, checker.logs);
    }
}
