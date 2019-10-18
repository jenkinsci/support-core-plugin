package com.cloudbees.jenkins.support.api;

import hudson.model.AbstractModelObject;
import hudson.model.Descriptor;

/**
 * Descriptor of {@link ObjectComponent}
 * 
 * @param <T> The object type that the {@link ObjectComponent} handles.
 */
public class ObjectComponentDescriptor<T extends AbstractModelObject> extends Descriptor<ObjectComponent<T>> {

}
