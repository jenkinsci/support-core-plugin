/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Content that is stored as a file on disk and is not filtered. If you want the content to be filtered, use the
 * {@link FileContent} class.
 *
 * @author Stephen Connolly, M Ramón León
 */
// The name is so because we have to keep compatibility with the existing FileContent which is pre-filtered.
public class UnfilteredFileContent extends Content {
    // to keep compatibility
    protected final File file;
    private BaseFileContent baseFileContent;
    private final Logger LOGGER = Logger.getLogger(this.getClass().getName());

    public UnfilteredFileContent(String name, File file) {
        this(name, file, -1);
    }

    public UnfilteredFileContent(String name, File file, long maxSize) {
        super(name);
        this.file = file;
        baseFileContent = createBaseFileContent(file, maxSize);
    }

    public UnfilteredFileContent(String name, String[] filterableParameters, File file) {
        this(name, filterableParameters, file, -1);
    }

    public UnfilteredFileContent(String name, String[] filterableParameters, File file, long maxSize) {
        super(name, filterableParameters);
        this.file = file;
        baseFileContent = createBaseFileContent(file, maxSize);
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        baseFileContent.writeTo(os);
    }

    @Override
    public long getTime() throws IOException {
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

    private BaseFileContent createBaseFileContent(File file, long maxSize) {
        return new BaseFileContent(file, this::getInputStream, maxSize, s -> s);
    }

    @Override
    public boolean shouldBeFiltered() {
        return false;
    }
}
