package com.cloudbees.jenkins.support.api;

public class ContentData {
    private final String name;
    private final boolean shouldAnonymize;

    public ContentData(String name, boolean shouldAnonymize) {
        this.name = name;
        this.shouldAnonymize = shouldAnonymize;
    }

    public String getName() {
        return name;
    }

    public boolean isShouldAnonymize() {
        return shouldAnonymize;
    }
}
