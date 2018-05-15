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

import hudson.Functions;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Represents a mapping from some original string to a replacement. Useful both as an individual ContentFilter as well
 * as a persistable class for consistent anonymization mappings.
 *
 * @see ContentMappings
 * @since TODO
 */
@Immutable
@Restricted(NoExternalUse.class)
public class ContentMapping implements ContentFilter, Comparable<ContentMapping> {
    private static final String ALT_SEPARATOR = " » ";
    private static final Comparator<ContentMapping> BY_LENGTH = Comparator.comparingLong(mapping -> mapping.getOriginal().length());
    private static final Comparator<ContentMapping> BY_NAME = Comparator.comparing(ContentMapping::getOriginal);
    private static final Comparator<ContentMapping> COMPARATOR = BY_LENGTH.reversed().thenComparing(BY_NAME);

    private final String original;
    private final Pattern pattern;
    private final String replacement;
    private final int hashCode;

    private ContentMapping(@Nonnull String original, @Nonnull Pattern pattern, @Nonnull String replacement) {
        this.original = original;
        this.pattern = pattern;
        this.replacement = replacement;
        this.hashCode = original.hashCode();
    }

    /**
     * Constructs a ContentMapping using an original and replacement value.
     */
    public static ContentMapping of(@Nonnull String original, @Nonnull String replacement) {
        return new ContentMapping(original, generatePattern(original), replacement);
    }


    private static Pattern generatePattern(String original) {
        String alternative = original.replace("/", ALT_SEPARATOR);
        String regex = Stream.of(original, Functions.escape(original), alternative, Functions.escape(alternative))
                .distinct()
                .map(Pattern::quote)
                .collect(joining("|", "\\b(", ")\\b"));
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * @return the original string to replace
     */
    public @Nonnull String getOriginal() {
        return original;
    }

    /**
     * @return the replacement string that the originals are replaced with
     */
    public @Nonnull String getReplacement() {
        return replacement;
    }

    @Override
    public @Nonnull String filter(@Nonnull String input) {
        return pattern.matcher(input).replaceAll(replacement);
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

    @Override
    public int compareTo(ContentMapping o) {
        return COMPARATOR.compare(this, o);
    }

    private Object writeReplace() {
        SerializationProxy proxy = new SerializationProxy();
        proxy.original = original;
        proxy.pattern = pattern;
        proxy.replacement = replacement;
        return proxy;
    }

    private static class SerializationProxy {
        private String original;
        private Pattern pattern;
        private String replacement;

        private Object readResolve() {
            return new ContentMapping(original, pattern, replacement);
        }
    }
}
