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

import com.cloudbees.jenkins.support.api.Content;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents some content in a support bundle which will be filtered previously to the zip created.
 */
public abstract class PrefilteredContent extends Content {

    protected PrefilteredContent(String name) {
        super(name);
    }

    protected PrefilteredContent(String name, String... tokens) {
        super(name, tokens);
    }

    /**
     * Write the component in the bundle filtering the content
     * @param os OutputStream where write the content
     * @param filter ContentFilter to apply
     * @throws IOException
     */
    public abstract void writeTo(OutputStream os, ContentFilter filter) throws IOException;

    @Override
    public final boolean shouldBeFiltered() {
        return false;
    }
}
