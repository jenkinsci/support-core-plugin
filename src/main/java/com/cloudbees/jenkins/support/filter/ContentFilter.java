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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Saveable;
import java.io.IOException;

/**
 * Provides a strategy to filter support bundle written contents. This is primarily useful to anonymize data written
 * to the bundle, though more complex filtering can be achieved.
 * A {@link ContentFilter} may produce {@link ContentMappings} during {@link ContentFilter#reload()} and
 * {@link ContentFilter#filter(String)} that are automatically persisted. It is therefore important to use
 * {@link BulkChange} when reloading the filter as well as when launching a task that performs the filtering:
 *
 * <pre>
 * {@code
 *   ContentFilter filter = ContentFilter.ALL;
 *   try {
 *     ContentFilter.reloadAndSaveMappings(filter);
 *   } catch (IOException e) {
 *      ...
 *   }
 *   [...]
 *   try (BulkChange change = new BulkChange(ContentFilter.bulkChangeTarget())) {
 *     doSomething(filter);
 *     change.commit();
 *   }
 * }</pre>
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
     * Provides a noop ContentFilter that pass through the value.
     */
    ContentFilter NONE = new NoneFilter();

    /**
     * Filters a line or snippet of text.
     *
     * @param input input data to filter
     * @return the filtered input data
     */
    @NonNull
    String filter(@NonNull String input);

    /**
     * Ensure that the filter has been loaded at least once.
     * @deprecated use reload() instead
     */
    @Deprecated
    default void ensureLoaded() {}

    /**
     * Reloads the state of this filter. This may be implemented to rescan for more items to filter.
     */
    default void reload() {}

    /**
     * An utility method to filter a text only when both, the filter and the text are not null and the text is not empty
     * too.
     * @param filter the filter to use when filtering
     * @param text the text to filter
     * @return the text filtered if it is not empty and the filter is not null
     */
    static String filter(@NonNull ContentFilter filter, @CheckForNull String text) {
        if (text != null && !text.isEmpty()) {
            return filter.filter(text);
        } else {
            return text;
        }
    }

    /**
     * Return the target for {@link BulkChange}.
     *
     * @return a {@link Saveable}
     */
    static Saveable bulkChangeTarget() {
        return ContentMappings.get();
    }

    /**
     * Reloads the state of this filter and commit .
     *
     * @throws IOException if reload or saving failed
     */
    static void reloadAndSaveMappings(@NonNull ContentFilter filter) throws IOException {
        ContentMappings mappings = ContentMappings.get();
        // If already in a BulkChange transaction, just reload
        if (BulkChange.contains(mappings)) {
            mappings.reload();
            filter.reload();
        } else {
            try (BulkChange change = new BulkChange(mappings)) {
                mappings.reload();
                filter.reload();
                change.commit();
            }
        }
    }
}
