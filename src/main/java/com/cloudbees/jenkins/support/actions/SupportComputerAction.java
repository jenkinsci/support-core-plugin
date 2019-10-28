package com.cloudbees.jenkins.support.actions;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * A {@link SupportObjectAction} applicable to {@link Computer}.
 */
public class SupportComputerAction extends SupportObjectAction<Computer> {

    @DataBoundConstructor
    public SupportComputerAction(Computer target) {
        super(target);
    }

    @Override
    public String getDisplayName() {
        return Messages.SupportComputerAction_DisplayName();
    }

    @Extension
    public static class Factory extends TransientActionFactory<Computer> {

        @Override
        public Class<Computer> type() {
            return Computer.class;
        }

        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull Computer computer) {
            if (computer == Jenkins.get().toComputer()) {
                return Collections.emptyList();
            }
            return Collections.singleton(new SupportComputerAction(computer));
        }
    }
}
