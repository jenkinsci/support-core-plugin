package com.cloudbees.jenkins.support.actions;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * A {@link SupportObjectAction} applicable to {@link AbstractItem}.
 */
public class SupportAbstractItemAction extends SupportObjectAction<AbstractItem> {

    private final Logger logger = Logger.getLogger(SupportAbstractItemAction.class.getName());

    @DataBoundConstructor
    public SupportAbstractItemAction(AbstractItem target) {
        super(target);
    }

    @Override
    public String getDisplayName() {
        return Messages.SupportItemAction_DisplayName();
    }
    
    @Extension
    public static class Factory extends TransientActionFactory<AbstractItem> {

        @Override
        public Class<AbstractItem> type() {
            return AbstractItem.class;
        }

        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull AbstractItem item) {
            return Collections.singleton(new SupportAbstractItemAction(item));
        }
    }
}
