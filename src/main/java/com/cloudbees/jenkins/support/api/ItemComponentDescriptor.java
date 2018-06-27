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

import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.TopLevelItem;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for {#link Descriptor}s of {@link ItemComponent}.
 */
public abstract class ItemComponentDescriptor extends Descriptor<ItemComponent> {

    /**
     * @return A description of the content that the associated {@link ItemComponent}
     *         will add to the support bundle
     */
    @Override
    public abstract String getDisplayName();

    /**
     * Used to decide whether this descriptor applies to a given {@link TopLevelItem}.
     * @param item The {@link TopLevelItem} the descriptor would be associated with
     * @return true if the descriptor is applicable to the item, otherwise false.
     */
    public abstract boolean isApplicable(TopLevelItem item);

    /**
     * Get all registered {@link ItemComponentDescriptor}s that apply to the given {@link TopLevelItem}. 
     * Filters based on {@link ItemComponentDescriptor#isApplicable} and {@link DescriptorVisibilityFilter}.
     * @param item the {@link TopLevelItem} that will be used to filter the descriptors
     * @return the relevant {@link ItemComponentDescriptor}s for the given item
     */
    public static List<ItemComponentDescriptor> getDescriptors(TopLevelItem item) {
        return DescriptorVisibilityFilter
                .apply(item, ExtensionList.lookup(ItemComponentDescriptor.class))
                .stream()
                .filter(d -> d.isApplicable(item))
                .collect(Collectors.toList());
    }

}
