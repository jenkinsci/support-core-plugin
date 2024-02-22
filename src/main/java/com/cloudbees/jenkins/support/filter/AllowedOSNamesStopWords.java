package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class AllowedOSNamesStopWords implements StopWords {

    @NonNull
    @Override
    public Set<String> getWords() {
        // JENKINS-54688
        return new HashSet<>(Arrays.asList(
                "linux",
                "windows",
                "win",
                "mac",
                "macos",
                "macosx",
                "mac os x",
                "ubuntu",
                "debian",
                "fedora",
                "red hat",
                "sunos",
                "freebsd"));
    }
}
