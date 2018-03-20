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

import com.cloudbees.jenkins.support.SupportLogFormatter;
import hudson.FilePath;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.NoSuchFileException;

/**
 * Content that is stored as a file on a remote disk
 *
 * @author Stephen Connolly
 */
public class FilePathContent extends Content {

    private static boolean isFileNotFound(Throwable e) {
        return e instanceof FileNotFoundException || e instanceof NoSuchFileException;
    }

    private final FilePath file;

    public FilePathContent(String name, FilePath file) {
        this(new ContentData(name, false), file);
    }

    public FilePathContent(ContentData contentData, FilePath file) {
        super(contentData);
        this.file = file;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try {
            if (shouldAnonymize) {
                FileContent.anonymizeOutput(os, file.read(), -1);
            } else {
                file.copyTo(os);
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (IOException e) {
            if (isFileNotFound(e) || isFileNotFound(e.getCause())) {
                OutputStreamWriter osw = new OutputStreamWriter(os, "utf-8");
                try {
                    PrintWriter pw = getPrintWriter(osw, true);
                    try {
                        pw.println("--- WARNING: Could not attach " + file.getRemote() + " as it cannot currently be found ---");
                        pw.println();
                        SupportLogFormatter.printStackTrace(e, pw);
                    } finally {
                        pw.flush();
                    }
                } finally {
                    osw.flush();
                }
            } else {
                throw e;
            }
        }
    }

    @Override
    public long getTime() throws IOException {
        try {
            return file.lastModified();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
