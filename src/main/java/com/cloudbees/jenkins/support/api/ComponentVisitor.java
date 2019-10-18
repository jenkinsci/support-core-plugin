package com.cloudbees.jenkins.support.api;

/**
 * @author Allan Burdajewicz
 */
public abstract class ComponentVisitor {
    
    public abstract <T extends Component> void visit(Container container, T component);
}
