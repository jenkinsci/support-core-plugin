/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import hudson.Util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Temporary file content, auto-deleted after {@link #writeTo(OutputStream)}.
 */
public class TemporaryFileContent extends FileContent {

    private File f;

    public TemporaryFileContent(String name, File file) {
        this(new ContentData(name, false), file);
    }

    public TemporaryFileContent(ContentData contentData, File file) {
        super(contentData, file);
        f = file;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try {
            super.writeTo(os);
        } finally {
            delete();
        }
    }

    private void delete() {
        try {
            Util.deleteFile(f);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete tmp file " + f.getAbsolutePath(), e);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(TemporaryFileContent.class.getName());
}