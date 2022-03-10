package com.cloudbees.jenkins.support.actions;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;

/**
 * A {@link SupportObjectAction} applicable to {@link Run}.
 */
public class SupportRunAction extends SupportObjectAction<Run> {

    @DataBoundConstructor
    public SupportRunAction(Run target) {
        super(target);
    }

    @Override
    public String getDisplayName() {
        return Messages.SupportRunAction_DisplayName(getObject().getParent().getTaskNoun());
    }

    @Override
    protected String getBundleNameQualifier() {
        return "build";
    }

    @Extension
    public static class Factory extends TransientActionFactory<Run> {

        @Override
        public Class<Run> type() {
            return Run.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Run run) {
            return Collections.singleton(new SupportRunAction(run));
        }
    }
}
