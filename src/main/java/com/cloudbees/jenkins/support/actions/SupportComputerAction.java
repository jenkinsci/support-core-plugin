package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.filter.ContentFilters;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
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

    @Override
    protected String getBundleNameQualifier() {
        return "agent";
    }
    
    @Restricted(NoExternalUse.class) // stapler
    @SuppressWarnings("unused") // used by Stapler
    public boolean isAnonymized() {
        return ContentFilters.get().isEnabled();
    }
    
    @Extension
    public static class Factory extends TransientActionFactory<Computer> {

        @Override
        public Class<Computer> type() {
            return Computer.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Computer computer) {
            if (computer == Jenkins.get().toComputer()) {
                return Collections.emptyList();
            }
            return Collections.singleton(new SupportComputerAction(computer));
        }
    }
}
