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
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps an OutputStream by filtering written lines using a provided ContentFilter.
 * Data written to an instance of this will be decoded on the fly using the provided charset, and each line
 * written is filtered.
 *
 * @see ContentFilter
 * @see FilteredWriter
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class FilteredOutputStream extends FilterOutputStream {

    static final Pattern EOL = Pattern.compile("\r?\n");
    static final int DEFAULT_DECODER_CAPACITY = 1024;
    private static final String UNKNOWN_INPUT = "\uFFFD";

    @GuardedBy("this")
    private final ByteBuffer encodedBuf = ByteBuffer.allocate(256);
    @GuardedBy("this")
    private CharBuffer decodedBuf;
    private final Charset charset;
    @GuardedBy("this")
    private final CharsetDecoder decoder;
    private final ContentFilter contentFilter;

    /**
     * Constructs a filtered stream using the provided filter and assuming UTF-8.
     *
     * @param out           output stream to write filtered content to
     * @param contentFilter content filter to apply to lines written through this stream
     */
    public FilteredOutputStream(@Nonnull OutputStream out, @Nonnull ContentFilter contentFilter) {
        this(out, StandardCharsets.UTF_8, contentFilter);
    }

    /**
     * Constructs a filtered stream using the provided filter and charset.
     *
     * @param out           output stream to write filtered content to
     * @param charset       character set to use for decoding and encoding bytes written to this stream
     * @param contentFilter content filter to apply to lines written through this stream
     */
    public FilteredOutputStream(@Nonnull OutputStream out, @Nonnull Charset charset, @Nonnull ContentFilter contentFilter) {
        super(out);
        this.charset = charset;
        this.decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith(UNKNOWN_INPUT);
        this.contentFilter = contentFilter;
    }

    private void ensureBuffer() {
        if (decodedBuf == null) {
            decodedBuf = CharBuffer.allocate(DEFAULT_DECODER_CAPACITY);
        }
    }

    @Override
    public synchronized void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public synchronized void write(@Nonnull byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public synchronized void write(@Nonnull byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            int toCopy = Math.min(encodedBuf.remaining(), len);
            if (toCopy == 0) throw new IllegalStateException("Cannot write zero bytes; " + encodedBuf.toString());
            encodedBuf.put(b, off, toCopy);
            decodeFilterFlushLines(false);
            len -= toCopy;
            off += toCopy;
        }
        filterFlushLines();
    }

    /**
     * Flushes the current buffered contents and filters them as is.
     */
    @Override
    public synchronized void flush() throws IOException {
        ensureBuffer();
        if (decodedBuf.position() > 0) {
            decodedBuf.flip();
            String contents = decodedBuf.toString();
            String filtered = contentFilter.filter(contents);
            out.write(filtered.getBytes(charset));
            decodedBuf.clear();
        }
        out.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        decodeFilterFlushLines(true);
        flush();
        reset();
        out.close();
    }

    private void decodeFilterFlushLines(boolean endOfInput) throws IOException {
        ensureBuffer();
        encodedBuf.flip();
        while (true) {
            CoderResult result = decoder.decode(encodedBuf, decodedBuf, endOfInput);
            if (result.isUnderflow()) {
                // keep accepting; can't decode this yet
                break;
            } else if (result.isOverflow()) {
                if (!filterFlushLines()) {
                    // unable to make space, need to resize
                    decodedBuf.flip();
                    decodedBuf = CharBuffer.allocate(decodedBuf.capacity() * 2).put(decodedBuf);
                }
            } else {
                throw new IllegalStateException("CharsetDecoder is mis-configured. Result: " + result);
            }
        }
        encodedBuf.compact();
    }

    private boolean filterFlushLines() throws IOException {
        ensureBuffer();
        boolean flushed = false;
        if (decodedBuf.position() > 0) {
            decodedBuf.flip();
            Matcher matcher = EOL.matcher(decodedBuf);
            int start = 0;
            while (matcher.find()) {
                int end = matcher.end();
                String line = decodedBuf.subSequence(start, end).toString();
                String filtered = contentFilter.filter(line);
                out.write(filtered.getBytes(charset));
                start = end;
                flushed = true;
            }
            decodedBuf.position(start);
            decodedBuf.compact();
        }
        return flushed;
    }

    /**
     * Resets the state of this stream's decoders and buffers.
     */
    public synchronized void reset() {
        encodedBuf.clear();
        ensureBuffer();
        if (decodedBuf.capacity() > DEFAULT_DECODER_CAPACITY) {
            this.decodedBuf = CharBuffer.allocate(DEFAULT_DECODER_CAPACITY);
        } else {
            decodedBuf.clear();
        }
        decoder.reset();
    }

    /**
     * @return a FilteredWriter view of this stream's underlying OutputStream
     */
    public synchronized FilteredWriter asWriter() {
        CharBuffer initialBuffer = decodedBuf;
        decodedBuf = null;
        return new FilteredWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), contentFilter, initialBuffer);
    }
}
