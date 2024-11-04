package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Set;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class DefaultStopWords implements StopWords {

    @NonNull
    @Override
    public Set<String> getWords() {
        return Set.of(
                "agent",
                "jenkins",
                "node",
                "master",
                "computer",
                "item",
                "label",
                "view",
                "all",
                "unknown",
                "user",
                "anonymous",
                "authenticated",
                "everyone",
                "system",
                "admin",
                "up",
                "running",
                Jenkins.VERSION,
                "tmp",
                "git",
                "retry",
                "vault",
                "exists");
    }
}
