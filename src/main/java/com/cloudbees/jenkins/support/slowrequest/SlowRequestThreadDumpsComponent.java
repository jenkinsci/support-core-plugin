package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;

/**
 * Contributes thread dumps slow request reports into the support bundle.
 *
 * @author Ignacio Roncero
 */
@Extension
public class SlowRequestThreadDumpsComponent extends UnfilteredFileListCapComponent {

    @NonNull
    @Override
    public String getDisplayName() {
        return "Thread Dumps on Slow Request Records";
    }

    @Override
    public void addContents(@NonNull Container container) {
        SlowRequestThreadDumpsGenerator generator =
                ExtensionList.lookup(SlowRequestThreadDumpsGenerator.class).get(SlowRequestThreadDumpsGenerator.class);
        if (generator != null && generator.logs.getSize() > 0) {
            super.addContents(container, generator.logs);
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    @Override
    public SupportAction.PreChooseOptions[] getDefautlPreChooseOptions() {
        return new SupportAction.PreChooseOptions[]{ SupportAction.PreChooseOptions.Default, SupportAction.PreChooseOptions.ConfigurationFiles, SupportAction.PreChooseOptions.PerformanceData };
    }
}
