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

import javax.annotation.Nonnull;

/**
 * Provides a ContentFilter that aggregates and applies all registered filters.
 *
 * TODO: move to static methods in ContentFilter
 * @since TODO
 */
public enum MasterContentFilter implements ContentFilter {
    instance;

    @Nonnull
    @Override
    public String filter(@Nonnull String input) {
        String filtered = input;
        for (ContentFilter filter : ExtensionList.lookup(ContentFilter.class)) {
            filtered = filter.filter(filtered);
        }
        return filtered;
    }

    @Override
    public void reload() {
        ExtensionList.lookup(ContentFilter.class).forEach(ContentFilter::reload);
    }
}
