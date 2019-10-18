/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractModelObject;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Represents a component of a support bundle for a specific model object.
 *
 * <p>
 * This is the unit of user consent; when creating a bundle for this object, the user would enable/disable
 * individual components.
 */
public abstract class ObjectComponent<T extends AbstractModelObject> extends Component
        implements Describable<ObjectComponent<T>>, ExtensionPoint {

    static final Logger LOGGER = Logger.getLogger(ObjectComponent.class.getName());

    /**
     * {@inheritDoc}
     */
    @Override
    public ObjectComponentDescriptor<T> getDescriptor() {
        return (ObjectComponentDescriptor<T>) Jenkins.get().getDescriptorOrDie(this.getClass());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addContents(@NonNull Container container) {
        // No op
    }

    /**
     * Add contents from a specific item to a container
     *
     * @param container the {@link Container}
     * @param item      the item
     */
    public abstract void addContents(@NonNull Container container, T item);

    /**
     * All applicable {@link ObjectComponent}s for the class.
     */
    public static <T extends AbstractModelObject> List<ObjectComponent<T>> allInstances(T item) {
        return Jenkins.get().getExtensionList(ObjectComponent.class).stream()
                .filter(component -> component.isApplicable(item.getClass()))
                .map(component -> ((ObjectComponent<T>) component))
                .filter(component -> component.isApplicable(item))
                .collect(Collectors.toList());
    }

    /**
     * All applicable {@link ObjectComponentDescriptor}s for the class.
     */
    public static <T extends AbstractModelObject> List<ObjectComponentDescriptor<T>> for_(T item) {
        return Jenkins.get().getExtensionList(ObjectComponent.class).stream()
                .filter(component -> component.isApplicable(item.getClass()))
                .map(component -> ((ObjectComponent<T>) component))
                .filter(component -> component.isApplicable(item))
                .map(ObjectComponent::getDescriptor)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        Class<T> type = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        return type.isAssignableFrom(clazz);
    }

    /**
     * Return if this component is applicable to a specific item.
     *
     * @param item the item
     * @return true if applicable
     */
    protected boolean isApplicable(T item) {
        return true;
    }

    /**
     * Control if the component should be selected by default, based on the applicable item
     *
     * @param item the item
     * @return true to select the component by default
     */
    public boolean isSelectedByDefault(T item) {
        return true;
    }
}

