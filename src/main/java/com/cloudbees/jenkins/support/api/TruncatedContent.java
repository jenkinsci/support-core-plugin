/*
 * The MIT License
 * 
 * Copyright (c) 2015 schristou88
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

import com.cloudbees.jenkins.support.timer.FileListCapComponent;

import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Content added to the support bundle that should be truncated.
 *
 * @author schristou88
 * @since 2.26
 */
public abstract class TruncatedContent extends Content {
  private int maxSize;

  public TruncatedContent(String name) {
    this(name, FileListCapComponent.MAX_FILE_SIZE);
  }

  public TruncatedContent(String name, int maxSize) {
    super(name);
    this.maxSize = maxSize;
  }

  @Override
  public void writeTo(OutputStream os) throws IOException {
    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new TruncatedOutputStream(os, maxSize), "UTF-8")));
    try {
      printTo(out);
    } finally {
      out.flush();
    }
  }

  /**
   * {$Content} that will be printed to a specific {$PrintWriter}.
   *
   * @param out PrintWriter to print to
   * @throws IOException if and error occurs while performing the operation.
   */
  protected abstract void printTo(PrintWriter out) throws IOException;

  public static class TruncatedOutputStream extends FilterOutputStream {
    private final int max;
    private int currentSize;

    public TruncatedOutputStream(OutputStream os, int max) {
      super(os);
      this.max = max;
      this.currentSize = 0;
    }

    @Override
    public void write(int b) throws IOException {
      if (max >= currentSize) {
        out.write(b);
        currentSize++;
      } else {
        throw new TruncationException("Max file size reached: " + max);
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      int writeSize = Math.min(max - currentSize, len);
      if (writeSize > 0) {
        out.write(b, off, writeSize);
        currentSize += writeSize;
      } else {
        throw new TruncationException("Max file size reached: " + max);
      }
    }
  }
}
