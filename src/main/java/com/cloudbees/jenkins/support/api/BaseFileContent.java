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
import com.cloudbees.jenkins.support.util.StreamUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.apache.commons.io.IOUtils;

/**
 * Utility class with the common logic to FileContent and UnfilteredFileContent.
 *
 * @author Stephen Connolly, M Ramón León
 */
final class BaseFileContent {

    @FunctionalInterface
    interface InputStreamSupplier {
        InputStream get() throws IOException;
    }

    private final File file;
    private final InputStreamSupplier inputStreamSupplier;

    /**
     * the function to filter secret text if comes from {@link com.cloudbees.jenkins.support.configfiles.XmlRedactedSecretFileContent} or {@link com.cloudbees.jenkins.support.api.LaunchLogsFileContent}
     */
    private final Function<String, String> secretsFilterFunction;

    private final long maxSize;
    private final boolean isBinary;

    private static final String ENCODING = "UTF-8";

    BaseFileContent(
            File file,
            InputStreamSupplier inputStreamSupplier,
            long maxSize,
            UnaryOperator<String> secretsFilterFunction) {
        this.file = file;
        this.inputStreamSupplier = inputStreamSupplier;
        this.secretsFilterFunction = secretsFilterFunction;
        this.maxSize = maxSize;
        this.isBinary = isBinary();
    }

    void writeTo(OutputStream os) throws IOException {
        try (InputStream is = inputStreamSupplier.get()) {
            if (maxSize == -1) {
                IOUtils.copy(is, os);
            } else {
                IOUtils.copy(new TruncatedInputStream(is, maxSize), os);
            }
        } catch (FileNotFoundException | NoSuchFileException e) { // TODO FilePathContent.isFileNotFound?
            OutputStreamWriter osw = new OutputStreamWriter(os, ENCODING);
            try {
                PrintWriter pw = new PrintWriter(osw, true);
                try {
                    pw.println("--- WARNING: Could not attach " + file + " as it cannot currently be found ---");
                    pw.println();
                    Functions.printStackTrace(e, pw);
                } finally {
                    pw.flush();
                }
            } finally {
                osw.flush();
            }
        }
    }

    void writeTo(OutputStream os, @NonNull ContentFilter filter) throws IOException {
        if (isBinary || filter == ContentFilter.NONE) {
            writeTo(os);
            return;
        }

        try (InputStream is = inputStreamSupplier.get()) {
            if (maxSize == -1) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, ENCODING))) {
                    String s;
                    while ((s = reader.readLine()) != null) {
                        String filtered = ContentFilter.filter(filter, secretsFilterFunction.apply(s)) + "\n";
                        IOUtils.write(filtered, os, ENCODING);
                    }
                }
            } else {
                try (TruncatedInputStreamReader reader = new TruncatedInputStreamReader(is, maxSize)) {
                    String s;
                    while ((s = reader.readLine()) != null) {
                        String filtered = ContentFilter.filter(filter, secretsFilterFunction.apply(s)) + "\n";
                        IOUtils.write(filtered, os, ENCODING);
                    }
                }
            }
        } catch (FileNotFoundException | NoSuchFileException e) { // TODO FilePathContent.isFileNotFound?
            OutputStreamWriter osw = new OutputStreamWriter(os, ENCODING);
            try {
                PrintWriter pw = new PrintWriter(osw, true);
                try {
                    pw.println("--- WARNING: Could not attach " + file + " as it cannot currently be found ---");
                    pw.println();
                    Functions.printStackTrace(e, pw);
                } finally {
                    pw.flush();
                }
            } finally {
                osw.flush();
            }
        }
    }

    long getTime() {
        return file.lastModified();
    }

    // Check if the file is binary or not
    private boolean isBinary() {
        try (InputStream in = inputStreamSupplier.get()) {
            long size = Files.size(file.toPath());
            if (size == 0) {
                // Empty file, so no need to check
                return true;
            }

            byte[] b = new byte[(size < StreamUtils.DEFAULT_PROBE_SIZE ? (int) size : StreamUtils.DEFAULT_PROBE_SIZE)];
            int read = in.read(b);
            if (read != b.length) {
                // Something went wrong, so better not to read line by line
                return true;
            }

            return StreamUtils.isNonWhitespaceControlCharacter(b);
        } catch (IOException e) {
            // If cannot be checked, then considered as binary, so we do not
            // read line by line
            return true;
        }
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

    private static final class TruncatedInputStreamReader extends BufferedReader {
        private long len;

        TruncatedInputStreamReader(InputStream is, long len) throws IOException {
            super(new InputStreamReader(is, ENCODING));
            this.len = len;
        }

        @Override
        public String readLine() throws IOException {
            if (len <= 0) {
                return null;
            }

            String line = super.readLine();
            if (line == null) {
                return null;
            }

            int length = line.getBytes(ENCODING).length;
            int toRead = (length <= len ? length : (int) len);
            len -= length;

            byte[] dest = new byte[toRead];
            System.arraycopy(line.getBytes(ENCODING), 0, dest, 0, toRead);

            return new String(dest, ENCODING);
        }
    }
}
