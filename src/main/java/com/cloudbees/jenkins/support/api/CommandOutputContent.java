package com.cloudbees.jenkins.support.api;

import hudson.model.Node;
import java.util.WeakHashMap;

/**
 * Content of a command output. You can only instantiate this content with
 * a builder method which launch the command immediately.
 */
public class CommandOutputContent extends StringContent {
    private CommandOutputContent(String name, String[] filterableParameters, String value) {
        super(name, filterableParameters, value);
    }

    public static CommandOutputContent runOnNode(Node node, String name, String... command) {
        return runOnNode(node, name, null, command);
    }

    public static CommandOutputContent runOnNode(
            Node node, String name, String[] filterableParameters, String... command) {
        String content = BaseCommandOutputContent.runOnNode(node, command);
        return new CommandOutputContent(name, filterableParameters, content);
    }

    public static CommandOutputContent runOnNodeAndCache(
            WeakHashMap<Node, String> cache, Node node, String name, String... command) {
        return runOnNodeAndCache(cache, node, name, null, command);
    }

    public static CommandOutputContent runOnNodeAndCache(
            WeakHashMap<Node, String> cache, Node node, String name, String[] filterableParameters, String... command) {
        String content = BaseCommandOutputContent.runOnNodeAndCache(cache, node, command);
        return new CommandOutputContent(name, filterableParameters, content);
    }
}
