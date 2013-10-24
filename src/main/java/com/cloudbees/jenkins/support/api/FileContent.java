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

import hudson.util.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Content that is stored as a file on disk.
 *
 * @author Stephen Connolly
 */
public class FileContent extends Content {

    private final File file;

    public FileContent(String name, File file) {
        super(name);
        this.file = file;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try {
            IOUtils.copy(file, os);
        } catch (FileNotFoundException e) {
            OutputStreamWriter osw = new OutputStreamWriter(os, "utf-8");
            try {
                PrintWriter pw = new PrintWriter(osw, true);
                try {
                    pw.println("--- WARNING: Could not attach " + file + " as it cannot currently be found ---");
                    pw.println();
                    e.printStackTrace(pw);
                } finally {
                    pw.flush();
                }
            } finally {
                osw.flush();
            }
        }
    }
}
