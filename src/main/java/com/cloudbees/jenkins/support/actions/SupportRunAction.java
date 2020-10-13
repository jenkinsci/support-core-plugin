package com.cloudbees.jenkins.support.actions;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
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

    @Extension
    public static class Factory extends TransientActionFactory<Run> {

        @Override
        public Class<Run> type() {
            return Run.class;
        }

        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull Run run) {
            return Collections.singleton(new SupportRunAction(run));
        }
    }
}
