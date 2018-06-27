/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.Describable;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import java.nio.file.Path;
import java.nio.file.Paths;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents a component of a support bundle that is associated with a particular
 * {@link TopLevelItem} in Jenkins.
 * 
 * These components are only available when using the support link in the side panel
 * of a specific {@link TopLevelItem}.
 */
public abstract class ItemComponent extends Component implements Describable<ItemComponent> {

    private TopLevelItem item;
    
    /**
     * All subclasses must have a constructor annotated with {@link DataBoundConstructor}.
     */
    public ItemComponent() {   
    }

    public TopLevelItem getItem() {
        return item;
    }

    /**
     * Get the item associated with this component and cast it to a particular type.
     * Useful for subclasses who restrict the kind of items that the component applies
     * to in their descriptor's {@link ItemComponentDescriptor#isApplicable} method.
     */
    public <T extends Item> T getItem(Class<T> clazz) {
        return clazz.cast(item);
    }

    public void setItem(TopLevelItem item) {
        this.item = item;
    }

    @Override
    public ItemComponentDescriptor getDescriptor() {
        return (ItemComponentDescriptor) Jenkins.get().getDescriptorOrDie(getClass());
    }
    
    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName();
    }

    /**
     * @return the preferred root output destination for all support bundle components for this item. 
     */
    public Path getItemRootDestination() {
        return Paths.get("items", item.getFullName());
    }
}
