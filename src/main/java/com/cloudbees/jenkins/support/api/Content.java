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

import com.cloudbees.jenkins.support.filter.ContentFilter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Represents some content in a support bundle.
 *
 * @author Stephen Connolly
 */
public abstract class Content {

    private final String name;

    /**
     * Parts of the name to be filtered
     */
    private String[] tokens;


    /**
     * Create a Content with this name. The name is not filtered so this constructor should be used exclusively when
     * the name is not prone to have sensitive information. If the name of this content is dynamically generated
     * and prone to have sensitive information, use the {@link #Content(String, String...)} constructor.
     * @param name name of the content.
     */
    protected Content(String name) {
        this(name, null);
    }

    /**
     * Set a name to this content with some filterable parts. The name could have elements with the form {0}, {1} that
     * will be filtered properly using the tokens. Example: Creating a content with
     * {code}new Content("nodes/{0}/file-descriptors.txt", "node") will filter the {0} part of the name with all the
     * declared {@link ContentFilter}.
     * This new constructor avoid having incorrectly filtered elements in the support bundle. For example, when having
     * a job called <i>nodes</i>. The <i>nodes</i> element in the file name of a file in the bundle shouldn't be filtered.
     *
     * The name is rendered using the {@link java.text.MessageFormat#format(String, Object...)} method after the filter.
     *
     * @param name name of the content
     * @param tokens strings to be filtered in the name
     */
    protected Content(String name, String... tokens) {
        this.name = name;
        this.tokens = tokens;
    }

    public String getName() {
        return name;
    }

    @CheckForNull
    public String[] getTokens() {
        return (tokens == null) ? null : Arrays.copyOf(tokens, tokens.length);
    }

    public abstract void writeTo(OutputStream os) throws IOException;

    public long getTime() throws IOException { return System.currentTimeMillis(); }

    /**
     * Indicates if this Content should be filtered when anonymization is enabled. When {@code true}, the contents written via
     * {@link #writeTo(OutputStream)} may be filtered by a {@link com.cloudbees.jenkins.support.filter.ContentFilter}.
     * When {@code false}, the contents are written without any filtering applied.
     *
     * @since TODO
     */
    public boolean shouldBeFiltered() {
        return true;
    }
}
