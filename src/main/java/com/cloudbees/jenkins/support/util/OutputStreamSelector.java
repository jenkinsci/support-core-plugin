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

package com.cloudbees.jenkins.support.util;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.jcip.annotations.GuardedBy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Dynamically selects either a textual or binary OutputStream destination based on simple content type probing.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class OutputStreamSelector extends OutputStream implements WrapperOutputStream {

    private final Supplier<OutputStream> binaryOutputStreamProvider;
    private final Supplier<OutputStream> textOutputStreamProvider;

    @GuardedBy("this")
    private ByteBuffer head;

    @GuardedBy("this")
    private OutputStream out;

    @GuardedBy("this")
    private boolean closed;

    /**
     * Constructs an OutputStreamSelector using the provided streams.
     *
     * @param binaryOutputStreamProvider a provider of an OutputStream to use if the contents written appear to be binary
     * @param textOutputStreamProvider a provider of an OutputStream to use if the contents written appear to be textual
     */
    public OutputStreamSelector(
            @NonNull Supplier<OutputStream> binaryOutputStreamProvider,
            @NonNull Supplier<OutputStream> textOutputStreamProvider) {
        this.binaryOutputStreamProvider = binaryOutputStreamProvider;
        this.textOutputStreamProvider = textOutputStreamProvider;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Stream is closed");
    }

    @Override
    public synchronized void write(int b) throws IOException {
        ensureOpen();
        write(new byte[] {(byte) b});
    }

    @Override
    public synchronized void write(@NonNull byte[] b, int off, int len) throws IOException {
        ensureOpen();
        if (len < 0) throw new IllegalArgumentException("Length cannot be negative. Got: " + len);
        if (len == 0) return;
        if (out == null) {
            probeContents(b, off, len);
        } else {
            out.write(b, off, len);
        }
    }

    private void probeContents(byte[] b, int off, int len) throws IOException {
        if (head == null) {
            head = ByteBuffer.allocate(StreamUtils.DEFAULT_PROBE_SIZE);
        }
        int toCopy = Math.min(head.remaining(), len);
        if (toCopy == 0)
            throw new IllegalStateException("No more room to buffer header, should have chosen stream by now");
        head.put(b, off, toCopy);
        if (head.hasRemaining()) return;
        chooseStream();
        if (toCopy < len) {
            write(b, off + toCopy, len - toCopy);
        }
    }

    private void chooseStream() throws IOException {
        if (head == null || head.position() == 0) {
            out = requireNonNull(textOutputStreamProvider.get(), "No OutputStream returned by text supplier");
        } else {
            head.flip().mark();
            boolean hasControlCharacter = StreamUtils.isNonWhitespaceControlCharacter(head);
            head.reset();
            out = requireNonNull(
                    (hasControlCharacter ? binaryOutputStreamProvider : textOutputStreamProvider).get(),
                    String.format("No OutputStream returned by %s supplier", hasControlCharacter ? "binary" : "text"));
            byte[] b = new byte[head.remaining()];
            head.get(b);
            write(b);
        }
        head = null;
    }

    @Override
    public synchronized void flush() throws IOException {
        ensureOpen();
        if (out == null) {
            chooseStream();
        }
        out.flush();
    }

    /**
     * Resets the state of this stream to allow for contents to be probed again.
     */
    public synchronized void reset() {
        ensureOpen();
        head = null;
        out = null;
    }

    @Override
    public synchronized void close() throws IOException {
        ensureOpen();
        try {
            if (out == null) {
                chooseStream();
            }
            out.close();
        } finally {
            closed = true;
        }
    }

    @Override
    public synchronized @NonNull OutputStream unwrap() throws IOException {
        ensureOpen();
        chooseStream();
        return out;
    }
}
