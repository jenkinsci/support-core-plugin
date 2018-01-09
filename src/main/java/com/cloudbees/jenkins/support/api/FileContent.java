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
import com.cloudbees.jenkins.support.util.Anonymizer;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Content that is stored as a file on disk.
 *
 * @author Stephen Connolly
 */
public class FileContent extends Content {

    protected final File file;
    private final long maxSize;

    public FileContent(String name, File file) {
        this(name, file, false, -1);
    }

    public FileContent(String name, File file, boolean shouldAnonymize) {
        this(name, file, shouldAnonymize, -1);
    }

    public FileContent(String name, File file, long maxSize) {
        super(name, false);
        this.file = file;
        this.maxSize = maxSize;
    }

    public FileContent(String name, File file, boolean shouldAnonymize, long maxSize) {
        super(name, shouldAnonymize);
        this.file = file;
        this.maxSize = maxSize;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try {
            if (shouldAnonymize) {
                anonymizeOutput(os, new FileInputStream(file), maxSize);
            } else {
                InputStream is = getInputStream();
                if (maxSize == -1) {
                    IOUtils.copy(is, os);
                } else {
                    try {
                        IOUtils.copy(new TruncatedInputStream(is, maxSize), os);
                    } finally {
                        is.close();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            OutputStreamWriter osw = new OutputStreamWriter(os, "utf-8");
            try {
                PrintWriter pw = getPrintWriter(osw, true);
                try {
                    pw.println("--- WARNING: Could not attach " + file + " as it cannot currently be found ---");
                    pw.println();
                    SupportLogFormatter.printStackTrace(e, pw);
                } finally {
                    pw.flush();
                }
            } finally {
                osw.flush();
            }
        }
    }

    public static void anonymizeOutput(OutputStream os, InputStream is, long maxSize) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            int currentSize = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = Anonymizer.anonymize(line) + System.lineSeparator();
                byte[] asBytes = line.getBytes(StandardCharsets.UTF_8);
                if (maxSize == -1 || currentSize + asBytes.length < maxSize) {
                    os.write(asBytes);
                    currentSize += asBytes.length;
                } else {
                    os.write(asBytes, 0, (int) (maxSize - currentSize));
                    break;
                }
            }
        }
    }

    /**
     * Instantiates the {@link InputStream} for the {@link #file}.
     * @return the {@link InputStream} for the {@link #file}.
     * @throws IOException if something goes wrong while creating the stream for reading #file.
     */
    protected InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public long getTime()  throws IOException {
        return file.lastModified();
    }

    /**
     * {@link InputStream} decorator that chops off the underlying stream at the
     * specified length
     *
     * @author Kohsuke Kawaguchi (from org.kohsuke.stapler)
     */
    private static final class TruncatedInputStream extends FilterInputStream {
        private long len;

        TruncatedInputStream(InputStream in, long len) {
            super(in);
            this.len = len;
        }

        @Override
        public int read() throws IOException {
            if (len <= 0) {
                return -1;
            }
            len--;
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int l) throws IOException {
            int toRead = (int) Math.min(l, len);
            if (toRead <= 0) {
                return -1;
            }

            int r = super.read(b, off, toRead);
            if (r > 0) {
                len -= r;
            }
            return r;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), len);
        }

        @Override
        public long skip(long n) throws IOException {
            long r = super.skip(Math.min(len, n));
            len -= r;
            return r;
        }
    }
}
