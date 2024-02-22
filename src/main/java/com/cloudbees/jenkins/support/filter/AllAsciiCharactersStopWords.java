package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class AllAsciiCharactersStopWords implements StopWords {

    @NonNull
    @Override
    public Set<String> getWords() {
        final int SPACE = ' '; // 20
        final int TILDE = '~'; // 126
        Set<String> singleChars = new HashSet<>(TILDE - SPACE + 1);

        for (char i = SPACE; i <= TILDE; i++) {
            singleChars.add(Character.toString(i));
        }

        return singleChars;
    }
}
