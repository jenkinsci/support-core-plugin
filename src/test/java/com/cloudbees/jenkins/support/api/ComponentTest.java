package com.cloudbees.jenkins.support.api;

import static org.junit.Assert.assertTrue;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComponentTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void hashValidation() throws Exception {
        long distinctCount = Jenkins.get().getExtensionList(Component.class).stream()
                .filter(component -> component.isApplicable(Jenkins.class))
                .filter(component -> component.getHash() != -1)
                .map(component -> component.getHash())
                .distinct()
                .count();
        long count = Jenkins.get().getExtensionList(Component.class).stream()
                .filter(component -> component.isApplicable(Jenkins.class))
                .filter(component -> component.getHash() != -1)
                .count();
        System.out.println("distinctCount: " + distinctCount + "; count: " + count);
        assertTrue(
                "It seems there is at least to components with the same HASH, the HASH should be uniq and lower as possible (Total components: "
                        + count + ", number of component with different hashes: " + distinctCount + ")",
                distinctCount == count);
    }
}
