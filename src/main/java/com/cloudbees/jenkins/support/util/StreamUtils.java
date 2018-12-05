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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Utility methods for handling files.
 */
@Restricted(NoExternalUse.class)
public final class StreamUtils {

    public static final int DEFAULT_PROBE_SIZE = 20;

    /**
     * Check if the content of a byte array is binary
     * @param b byte array to check
     * @return true if the content is binary (Non-white Control Characters)
     */
    public static boolean isNonWhitespaceControlCharacter(@NonNull byte[] b) {
        ByteBuffer head = ByteBuffer.allocate(DEFAULT_PROBE_SIZE);
        int toCopy = Math.min(head.remaining(), b.length);
        if (toCopy == 0) {
            throw new IllegalStateException("No more room to buffer header, should have chosen stream by now");
        }

        head.put(b, 0, toCopy);
        head.flip().mark();
        return isNonWhitespaceControlCharacter(head);
    }

    /**
     * Check if the content of a ByteBuffer is binary
     * @param head ByteBuffer to check
     * @return true if the content is binary (Non-white Control Characters)
     */
    public static boolean isNonWhitespaceControlCharacter(@NonNull ByteBuffer head) {
        boolean hasControlCharacter = false;

        while (head.hasRemaining()) {
            hasControlCharacter |= isNonWhitespaceControlCharacter(head.get());
        }

        return hasControlCharacter;
    }

    private static boolean isNonWhitespaceControlCharacter(byte b) {
        char c = (char) (b & 0xff);
        return Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r';
    }
}
