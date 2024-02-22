package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extension to contribute to the list of stop words in anonymization.
 */
public interface StopWords extends ExtensionPoint {

    /**
     * @return all {@link StopWords} extensions
     */
    static ExtensionList<StopWords> all() {
        return ExtensionList.lookup(StopWords.class);
    }

    /**
     * Return the stop words that will be added to {@link ContentMappings}.
     * @return a set of words
     */
    @NonNull
    Set<String> getWords();

    static class AllStopWords implements StopWords {

        @NonNull
        @Override
        public Set<String> getWords() {
            return all().stream()
                    .map(StopWords::getWords)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        }
    }
}
