package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

/**
 * Contributes slow request reports into the support bundle.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestComponent extends UnfilteredFileListCapComponent {
    @Inject
    SlowRequestChecker checker;

    @NonNull
    @Override
    public String getDisplayName() {
        return "Slow Request Records";
    }

    @Override
    public int getHash() { return 39; }

    @Override
    public void addContents(@NonNull Container container) {
        super.addContents(container, checker.logs);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }
}
