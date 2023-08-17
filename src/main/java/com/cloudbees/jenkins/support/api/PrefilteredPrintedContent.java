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
import com.cloudbees.jenkins.support.filter.FilteredOutputStream;
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import com.cloudbees.jenkins.support.util.IgnoreCloseWriter;
import com.cloudbees.jenkins.support.util.WrapperOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Content that is printed on demand and filtered by itself.
 *
 * @author M Ramón León
 */
public abstract class PrefilteredPrintedContent extends PrefilteredContent {
    public PrefilteredPrintedContent(String name) {
        super(name);
    }

    public PrefilteredPrintedContent(String name, String... filterableParameters) {
        super(name, filterableParameters);
    }

    @Override
    public final void writeTo(OutputStream os) throws IOException {
        writeTo(os, null);
    }

    @Override
    public void writeTo(OutputStream os, ContentFilter filter) throws IOException {
        final PrintWriter writer = getWriter(os);
        try {
            printTo(writer, filter);
        } finally {
            writer.flush();
        }
    }

    /**
     * When anonymization is enabled, we create an {@link com.cloudbees.jenkins.support.filter.FilteredWriter} directly from
     * the underlying {@link FilteredOutputStream} that prevents us from encoding and
     * then immediately decoding characters written to the returned {@link java.io.PrintStream}
     * when filtering.
     */
    private PrintWriter getWriter(OutputStream os) throws IOException {
        if (os instanceof WrapperOutputStream) {
            OutputStream out = ((WrapperOutputStream) os).unwrapRecursively();
            if (out instanceof FilteredOutputStream) {
                FilteredOutputStream filteredStream = (FilteredOutputStream) out;
                return new PrintWriter(new IgnoreCloseWriter(filteredStream.asWriter()));
            }
        }
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)));
    }

    protected void printTo(PrintWriter out) throws IOException {
        printTo(out, null);
    }

    protected abstract void printTo(PrintWriter out, ContentFilter filter) throws IOException;
}
