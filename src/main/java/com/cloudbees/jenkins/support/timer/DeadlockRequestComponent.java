package com.cloudbees.jenkins.support.timer;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.api.Container;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

/**
 * @author stevenchristou
 *         Date: 4/23/14
 *         Time: 4:50 PM
 */
@Extension
public class DeadlockRequestComponent extends UnfilteredFileListCapComponent {
    @Inject
    DeadlockTrackChecker checker;

    @NonNull
    @Override
    public String getDisplayName() {
        return "Deadlock Records";
    }

    @Override
    public void addContents(@NonNull Container container) {
        super.addContents(container, checker.logs);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }

}
