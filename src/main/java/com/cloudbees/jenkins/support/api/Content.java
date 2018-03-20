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

import com.cloudbees.jenkins.support.util.AnonymizedPrintWriter;
import com.cloudbees.jenkins.support.util.Anonymizer;
import hudson.model.Node;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * Represents some content in a support bundle.
 *
 * @author Stephen Connolly
 */
public abstract class Content {

    private final String name;
    protected final boolean shouldAnonymize;

    protected Content(String name) {
        this(new ContentData(name, false));
    }

    protected Content(ContentData contentData) {
        name = contentData.getName();
        shouldAnonymize = contentData.isShouldAnonymize();
    }

    public String getName() {
        return name;
    }

    protected String getNodeName(Node node) {
        return getNodeName(node, shouldAnonymize);
    }

    protected static String getNodeName(Node node, boolean shouldAnonymize) {
        if (shouldAnonymize) {
            return node instanceof Jenkins ? "master" : Anonymizer.anonymize(node.getNodeName());
        }
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }

    protected PrintWriter getPrintWriter(Writer writer) {
        if (shouldAnonymize) {
            return new AnonymizedPrintWriter(writer);
        }
        return new PrintWriter(writer);
    }

    protected PrintWriter getPrintWriter(Writer writer, boolean autoFlush) {
        if (shouldAnonymize) {
            return new AnonymizedPrintWriter(writer, autoFlush);
        }
        return new PrintWriter(writer, autoFlush);
    }

    public abstract void writeTo(OutputStream os) throws IOException;

    public long getTime() throws IOException { return System.currentTimeMillis(); }
}
