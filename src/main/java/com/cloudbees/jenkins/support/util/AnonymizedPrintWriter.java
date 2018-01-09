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
package com.cloudbees.jenkins.support.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

/**
 * {@link PrintWriter} that anonymizes all input strings.
 */
public class AnonymizedPrintWriter extends PrintWriter {

    public AnonymizedPrintWriter(Writer out) {
        super(out);
    }

    public AnonymizedPrintWriter(Writer out, boolean autoFlush) {
        super(out, autoFlush);
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public AnonymizedPrintWriter(OutputStream out) {
        super(out);
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public AnonymizedPrintWriter(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public AnonymizedPrintWriter(String fileName) throws FileNotFoundException {
        super(fileName);
    }

    public AnonymizedPrintWriter(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(fileName, csn);
    }

    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public AnonymizedPrintWriter(File file) throws FileNotFoundException {
        super(file);
    }

    public AnonymizedPrintWriter(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, csn);
    }

    @Override
    public void write(String s) {
        super.write(Anonymizer.anonymize(s));
    }

    @Override
    public void print(String s) {
        super.print(Anonymizer.anonymize(s));
    }

    @Override
    public void println(String x) {
        super.println(Anonymizer.anonymize(x));
    }
}
