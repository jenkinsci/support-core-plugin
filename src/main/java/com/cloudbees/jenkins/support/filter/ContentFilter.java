/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package com.cloudbees.jenkins.support.filter;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

import javax.annotation.Nonnull;

/**
 * Provides a strategy to filter support bundle written contents. This is primarily useful to anonymize data written
 * to the bundle, though more complex filtering can be achieved.
 *
 * @since TODO
 */
public interface ContentFilter extends ExtensionPoint {

    /**
     * @return all ContentFilter extensions
     */
    static ExtensionList<ContentFilter> all() {
        return ExtensionList.lookup(ContentFilter.class);
    }

    /**
     * Provides a ContentFilter that combines all registered ContentFilter extensions.
     */
    ContentFilter ALL = new AllContentFilters();

    /**
     * Filters a line or snippet of text.
     *
     * @param input input data to filter
     * @return the filtered input data
     */
    @Nonnull String filter(@Nonnull String input);

    /**
     * Reloads the state of this filter. This may be implemented to rescan for more items to filter.
     */
    default void reload() {
    }

}
