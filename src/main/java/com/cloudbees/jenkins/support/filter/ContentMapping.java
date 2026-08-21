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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Represents a mapping from some original string to a replacement. A persistable class for consistent anonymization mappings.
 *
 * @see ContentMappings
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class ContentMapping {

    private final String original;
    private final String replacement;
    private final int hashCode;

    // Mutable usage-tracking metadata, deliberately outside equals/hashCode/identity: when this mapping was last
    // known to be live (per a NameProvider) or actually matched during filtering. Drives grace-period eviction in
    // ContentMappings#evictStale(); a mapping that stops being touched eventually gets swept.
    private volatile Instant lastSeen;

    private ContentMapping(@NonNull String original, @NonNull String replacement) {
        this.original = original;
        this.replacement = replacement;
        this.lastSeen = Instant.now();
        this.hashCode = original.hashCode();
    }

    /**
     * Constructs a ContentMapping using an original and replacement value.
     */
    public static ContentMapping of(@NonNull String original, @NonNull String replacement) {
        return new ContentMapping(original, replacement);
    }

    /**
     * @return the original string to replace
     */
    public @NonNull String getOriginal() {
        return original;
    }

    /**
     * @return the replacement string that the originals are replaced with
     */
    public @NonNull String getReplacement() {
        return replacement;
    }

    /**
     * Marks this mapping as seen (live or actually matched) at the given time.
     */
    void touch(Instant at) {
        lastSeen = at;
    }

    /**
     * Marks this mapping as seen (live or actually matched) now.
     */
    void touch() {
        touch(Instant.now());
    }

    /**
     * @return the last time this mapping was known to be live or was actually matched during filtering
     */
    Instant getLastSeen() {
        return lastSeen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentMapping that = (ContentMapping) o;
        return Objects.equals(original, that.original);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private Object writeReplace() {
        SerializationProxy proxy = new SerializationProxy();
        proxy.original = original;
        proxy.replacement = replacement;
        proxy.lastSeen = lastSeen.toEpochMilli();
        return proxy;
    }

    private static class SerializationProxy {
        private String original;
        private String replacement;
        // Absent in XML written before this field existed; XStream leaves it at 0 in that case. long epoch millis
        // on the wire is fine here (unlike the in-memory field) since this is the serial form.
        private long lastSeen;

        private Object readResolve() {
            ContentMapping mapping = ContentMapping.of(original, replacement);
            // Treat "unknown" (0, i.e. pre-upgrade data) as "seen now" rather than "seen at the epoch" -- the
            // latter would make every mapping that already existed look instantly stale and get swept on the
            // very first reload after upgrading, defeating the "still catches stray references" property for
            // everything that predates this field.
            mapping.touch(lastSeen > 0 ? Instant.ofEpochMilli(lastSeen) : Instant.now());
            return mapping;
        }
    }
}
