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

import com.cloudbees.jenkins.support.filter.FilteredOutputStream;
import com.cloudbees.jenkins.support.util.IgnoreCloseOutputStream;
import com.cloudbees.jenkins.support.util.IgnoreCloseWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Content that is printed on demand.
 *
 * @author Stephen Connolly
 */
public abstract class PrintedContent extends GenerateOnDemandContent {
    public PrintedContent(String name) {
        super(name);
    }

    @Override
    public final void writeTo(OutputStream os) throws IOException {
        final PrintWriter writer = getWriter(os);
        try {
            printTo(writer);
        } finally {
            writer.flush();
        }
    }

    /**
     * When anonymization is enabled, we create an {@link FilteredWriter} directly from
     * the underlying {@link FilteredOutputStream} that prevents us from encoding and
     * then immediately decoding characters written to the returned {@link PrintStream}
     * when filtering.
     */
    private PrintWriter getWriter(OutputStream os) {
        if (os instanceof IgnoreCloseOutputStream) {
            IgnoreCloseOutputStream ignoreCloseStream = (IgnoreCloseOutputStream) os;
            // IgnoreCloseOutputStream always wraps the OutputStream, so we need to examine the type of the underlying stream.
            if (ignoreCloseStream.getUnderlyingStream() instanceof FilteredOutputStream) {
                FilteredOutputStream filteredStream = (FilteredOutputStream) ignoreCloseStream.getUnderlyingStream();
                return new PrintWriter(new IgnoreCloseWriter(filteredStream.asWriter()));
            }
        }
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)));
    }

    protected abstract void printTo(PrintWriter out) throws IOException;
}
