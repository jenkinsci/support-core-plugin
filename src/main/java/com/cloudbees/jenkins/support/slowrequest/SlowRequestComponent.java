package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

/**
 * Contributes slow request reports into the support bundle.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestComponent extends FileListCapComponent {
    @Inject
    SlowRequestChecker checker;

    @NonNull
    @Override
    public String getDisplayName() {
        return "Slow Request Records";
    }

    @Override
    public void addContents(@NonNull Container container) {
        super.addContents(container, checker.logs, false);
    }

    @Override
    public void addContents(@NonNull Container container, boolean shouldAnonymize) {
        super.addContents(container, checker.logs, shouldAnonymize);
    }
}
