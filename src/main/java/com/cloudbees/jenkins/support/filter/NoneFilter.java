package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link ContentFilter} that blindly pass the value through.
 */
@Restricted(NoExternalUse.class)
public class NoneFilter implements ContentFilter {
    @NonNull
    @Override
    public String filter(@NonNull String input) {
        return input;
    }
}
