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
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import hudson.Functions;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Content that is stored as a file on disk. The content is filtered with the {@link ContentFilter} defined in the
 * instance.
 *
 * @author Stephen Connolly
 */
public class FileContent extends PrefilteredContent {
    protected BaseFileContent baseFileContent;
    // to keep compatibility
    protected final File file;

    public FileContent(String name, File file) {
        this(name, file, -1);
    }

    public FileContent(String name, File file, long maxSize) {
        super(name);
        this.file = file;
        baseFileContent = createBaseFileContent(file, maxSize);
    }

    public FileContent(String name, String[] filterableParameters, File file) {
        this(name, filterableParameters, file, -1);
    }

    public FileContent(String name, String[] filterableParameters, File file, long maxSize) {
        super(name, filterableParameters);
        this.file = file;
        baseFileContent = createBaseFileContent(file, maxSize);
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        baseFileContent.writeTo(os);
    }

    @Override
    public void writeTo(OutputStream os, ContentFilter filter) throws IOException {
        baseFileContent.writeTo(os, filter);
    }

    @Override
    public long getTime() {
        return baseFileContent.getTime();
    }

    /**
     * Instantiates the {@link InputStream} for the {@link #file}.
     * @return the {@link InputStream} for the {@link #file}.
     * @throws IOException if something goes wrong while creating the stream for reading #file.
     */
    protected InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    protected String getSimpleValueOrRedactedPassword(String value) {
        return value;
    }

    private BaseFileContent createBaseFileContent(File file, long maxSize) {
        Supplier<InputStream> supplier = () -> {
            try {
                return getInputStream();
            } catch (IOException e) {
                return new ByteArrayInputStream(Functions.printThrowable(e).getBytes(StandardCharsets.UTF_8));
            }
        };
        return new BaseFileContent(file, supplier, maxSize, this::getSimpleValueOrRedactedPassword);
    }
}
