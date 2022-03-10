package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.filter.ContentFilters;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;

/**
 * A {@link SupportObjectAction} applicable to {@link AbstractItem}.
 */
public class SupportAbstractItemAction extends SupportObjectAction<AbstractItem> {

    @DataBoundConstructor
    public SupportAbstractItemAction(AbstractItem target) {
        super(target);
    }

    @Override
    public String getDisplayName() {
        return Messages.SupportItemAction_DisplayName(getObject().getPronoun());
    }

    @Override
    protected String getBundleNameQualifier() {
        return "item";
    }
    
    @Restricted(NoExternalUse.class) // stapler
    @SuppressWarnings("unused") // used by Stapler
    public boolean isAnonymized() {
        return ContentFilters.get().isEnabled();
    }

    @Extension
    public static class Factory extends TransientActionFactory<AbstractItem> {

        @Override
        public Class<AbstractItem> type() {
            return AbstractItem.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull AbstractItem item) {
            return Collections.singleton(new SupportAbstractItemAction(item));
        }
    }
}
