package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Actionable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Base class of Support Core actions.
 */
@Restricted(NoExternalUse.class)
public abstract class SupportChildAction extends Actionable implements Action {

    private final SupportAction supportAction;

    SupportChildAction(@NonNull SupportAction supportAction) {
        this.supportAction = supportAction;
        this.supportAction.addAction(this);
    }

    @Override
    public String getIconFileName() {
        return "/plugin/support-core/images/support.svg";
    }

    @Override
    public String getSearchUrl() {
        return getUrlName();
    }

    /**
     * @return the relative path of the {@link SupportChildAction} from root
     */
    @NonNull
    @SuppressWarnings("unused") // Stapler
    public String getContextUrl() {
        return supportAction.getUrlName() + "/" + getUrlName();
    }

    @NonNull
    @SuppressWarnings("unused") // Stapler
    public SupportAction getSupportAction() {
        return supportAction;
    }
}
