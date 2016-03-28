package com.cloudbees.jenkins.support.util;

import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

/**
 * Simple helper so we don't have to check {@code Jenkins.getInstance() != null} everywhere.
 * TODO: replace with Jenkins.getActiveInstance() when on core {@literal >=} 1.609
 */
@Restricted(NoExternalUse.class)
public final class Helper {
    /** Not instantiable. */
    private Helper() {
        throw new AssertionError("Not instantiable");
    }

    @Nonnull
    public static Jenkins getActiveInstance() {
        Jenkins instance = Jenkins.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Jenkins has not been started, or was already shut down");
        }
        return instance;
    }
}
