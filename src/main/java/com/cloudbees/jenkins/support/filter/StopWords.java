package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.util.Set;

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
}
