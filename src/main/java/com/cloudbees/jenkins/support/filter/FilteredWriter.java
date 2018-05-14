/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package com.cloudbees.jenkins.support.filter;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a Writer by filtering written lines using a provided ContentFilter.
 *
 * @see ContentFilter
 * @see FilteredOutputStream
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class FilteredWriter extends FilterWriter {

    static final Pattern EOL = Pattern.compile("\r?\n");
    static final int DEFAULT_BUFFER_CAPACITY = 1024;

    private final ContentFilter contentFilter;
    @GuardedBy("this")
    private CharBuffer buf;

    FilteredWriter(@Nonnull Writer writer, @Nonnull ContentFilter contentFilter, @Nonnull CharBuffer initialBuffer) {
        super(writer);
        this.contentFilter = contentFilter;
        this.buf = initialBuffer;
    }

    @Override
    public synchronized void write(int c) throws IOException {
        write(Character.toChars(c));
    }

    @Override
    public synchronized void write(@Nonnull char[] cbuf, int off, int len) throws IOException {
        while (len > 0) {
            if (!buf.hasRemaining()) filterFlushLines();
            if (!buf.hasRemaining()) {
                buf = CharBuffer.allocate(buf.capacity() * 2).put(buf);
            }
            int toCopy = Math.min(buf.remaining(), len);
            if (toCopy == 0) throw new IllegalStateException();
            buf.put(cbuf, off, toCopy);
            filterFlushLines();
            len -= toCopy;
            off += toCopy;
        }
        filterFlushLines();
    }

    @Override
    public synchronized void write(@Nonnull String str, int off, int len) throws IOException {
        while (len > 0) {
            if (!buf.hasRemaining()) filterFlushLines();
            if (!buf.hasRemaining()) {
                buf = CharBuffer.allocate(buf.capacity() * 2).put(buf);
            }
            int toCopy = Math.min(buf.remaining(), len);
            if (toCopy == 0) throw new IllegalStateException();
            buf.put(str, off, off + toCopy);
            filterFlushLines();
            len -= toCopy;
            off += toCopy;
        }
        filterFlushLines();
    }

    @Override
    public synchronized void flush() throws IOException {
        if (buf.position() > 0) {
            buf.flip();
            String original = buf.toString();
            String filtered = contentFilter.filter(original);
            out.write(filtered);
            buf.clear();
        }
        out.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        flush();
        out.close();
        buf.clear();
        if (buf.capacity() > DEFAULT_BUFFER_CAPACITY) {
            buf = CharBuffer.allocate(DEFAULT_BUFFER_CAPACITY);
        }
    }

    private void filterFlushLines() throws IOException {
        if (buf.position() > 0) {
            buf.flip();
            Matcher matcher = EOL.matcher(buf);
            int start = 0;
            while (matcher.find()) {
                int end = matcher.end();
                String line = buf.subSequence(start, end).toString();
                String filtered = contentFilter.filter(line);
                out.write(filtered);
                start = end;
            }
            buf.position(start);
            buf.compact();
        }
    }

}
