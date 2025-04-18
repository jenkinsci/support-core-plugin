package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jenkins.model.Jenkins;

@Extension
public class PluginsStopWords implements StopWords {
    @NonNull
    @Override
    public Set<String> getWords() {
        final Set<String> pluginsWords = new HashSet<>();
        Jenkins.get().pluginManager.getPlugins().forEach(pluginWrapper -> {
            pluginsWords.addAll(List.of(
                    pluginWrapper.getShortName().toLowerCase(Locale.ENGLISH).split("-")));
            pluginsWords.addAll(List.of(
                    pluginWrapper.getDisplayName().toLowerCase(Locale.ENGLISH).split(" ")));
        });
        return pluginsWords;
    }
}
