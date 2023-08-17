package com.cloudbees.jenkins.support.api;

import hudson.model.Node;
import java.util.WeakHashMap;

/**
 * Content of a command output. You can only instantiate this content with
 * a builder method which launches the command immediately. It doesn't pre filter the result.
 */
public class UnfilteredCommandOutputContent extends UnfilteredStringContent {
    private UnfilteredCommandOutputContent(String name, String[] filterableParameters, String value) {
        super(name, filterableParameters, value);
    }

    public static UnfilteredCommandOutputContent runOnNode(Node node, String name, String... command) {
        return runOnNode(node, name, null, command);
    }

    public static UnfilteredCommandOutputContent runOnNode(
            Node node, String name, String[] filterableParameters, String... command) {
        String content = BaseCommandOutputContent.runOnNode(node, command);
        return new UnfilteredCommandOutputContent(name, filterableParameters, content);
    }

    public static UnfilteredCommandOutputContent runOnNodeAndCache(
            WeakHashMap<Node, String> cache, Node node, String name, String... command) {
        return runOnNodeAndCache(cache, node, name, null, command);
    }

    public static UnfilteredCommandOutputContent runOnNodeAndCache(
            WeakHashMap<Node, String> cache, Node node, String name, String[] filterableParameters, String... command) {
        String content = BaseCommandOutputContent.runOnNodeAndCache(cache, node, command);
        return new UnfilteredCommandOutputContent(name, filterableParameters, content);
    }
}
