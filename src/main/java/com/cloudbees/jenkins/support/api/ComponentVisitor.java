package com.cloudbees.jenkins.support.api;

/**
 * A Visitor that define actions to carry out when visiting {@link Component}s. 
 */
public interface ComponentVisitor {

    /**
     * Call for each {@link Component}.
     *
     * @param container a {@link Container}
     * @param component the {@link Component} being visited
     * @param <T> must be a subclass of {@link Component}
     */
    <T extends Component> void visit(Container container, T component);
}
