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

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Allows for nested OutputStream wrappers and getting access to the unwrapped stream.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public interface WrapperOutputStream {
    /**
     * Unwraps this stream, potentially forcing unwritten data to be flushed.
     *
     * @return the underlying stream being wrapped
     * @throws IOException if an exception occurs preparing the unwrapped reference
     * @throws IllegalStateException if this stream is closed
     */
    @Nonnull OutputStream unwrap() throws IOException;

    /**
     * Unwraps this stream, recursively descending through any further WrapperOutputStream instances.
     *
     * @return the underlying stream being wrapped
     * @throws IOException if an exception occurs during any unwrap stage
     * @throws IllegalStateException if this stream is closed
     */
    default @Nonnull OutputStream unwrapRecursively() throws IOException {
        OutputStream out = unwrap();
        while (out instanceof WrapperOutputStream) {
            out = ((WrapperOutputStream) out).unwrap();
        }
        return out;
    }
}
