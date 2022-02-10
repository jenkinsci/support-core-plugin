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
import com.cloudbees.jenkins.support.filter.PasswordRedactor;
import hudson.FilePath;
import hudson.Functions;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
        super(name);
        this.file = file;
    }

    public FilePathContent(String name, String[] filterableParameters, FilePath file) {
        super(name, filterableParameters);
        this.file = file;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try {
            if (PasswordRedactor.FILES_WITH_SECRETS.contains(file.getName())) {
                copyRedacted(os);
            } else {
                file.copyTo(os);
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        } catch (IOException e) {
            if (isFileNotFound(e) || isFileNotFound(e.getCause())) {
                OutputStreamWriter osw = new OutputStreamWriter(os, "utf-8");
                try {
                    PrintWriter pw = new PrintWriter(osw, true);
                    try {
                        pw.println("--- WARNING: Could not attach " + file.getRemote() + " as it cannot currently be found ---");
                        pw.println();
                        Functions.printStackTrace(e, pw);
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

    private void copyRedacted(OutputStream os) throws IOException, InterruptedException {
        CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(FilteredOutputStream.UNKNOWN_INPUT);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.read(), charsetDecoder))) {
            String line;
            while ((line = br.readLine()) != null) {
                IOUtils.write(PasswordRedactor.get().redact(line), os, charsetDecoder.charset());
            }
        }
    }
}
