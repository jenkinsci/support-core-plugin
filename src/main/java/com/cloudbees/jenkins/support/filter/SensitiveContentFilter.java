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

import com.cloudbees.jenkins.support.util.WordReplacer;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Filters contents based on names provided by all {@linkplain NameProvider known sources}.
 *
 * @see NameProvider
 * @since TODO
 */
@Extension
@Restricted(NoExternalUse.class)
public class SensitiveContentFilter implements ContentFilter {

    private static final Logger LOGGER = Logger.getLogger(SensitiveContentFilter.class.getName());

    // A pattern and its two derived maps, published as one atomic unit so a concurrent reload() can never be
    // observed as a torn mix of an old pattern with newer maps (or vice versa) -- see replacementFor(). Since
    // the maps are always built from exactly the same names as the pattern, a match can never fail to resolve.
    // onMatch is captured at reload time rather than looked up per match, so filtering never depends on Jenkins
    // still being up -- ExtensionList.lookupSingleton throws once Jenkins.getInstanceOrNull() returns null, which
    // can happen while a bundle write is still draining during shutdown.
    private record Snapshot(
            Pattern pattern,
            Map<String, String> replacements,
            Map<String, ContentMapping> matched,
            Consumer<ContentMapping> onMatch) {
        // Matches nothing, so filter() is a no-op before the first reload() rather than risking an NPE.
        private static final Snapshot EMPTY = new Snapshot(Pattern.compile("(?!)"), Map.of(), Map.of(), m -> {});
    }

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public static SensitiveContentFilter get() {
        return ExtensionList.lookupSingleton(SensitiveContentFilter.class);
    }

    /**
     * Normalize case per code point, replicating what {@code Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE}
     * does internally when matching. This is <em>not</em> Unicode normalization (NFC/NFKC); it's a case-folding
     * operation: {@code Character.toLowerCase(Character.toUpperCase(codePoint))}.
     * <p>
     * Includes an identity fast path: returns the input unchanged when no code point would be altered, so
     * already-normalized strings share storage with their keys rather than duplicating the char array.
     *
     * @param value the input string
     * @return a case-normalized copy, or the input itself if nothing changed
     */
    static String normalizeCase(String value) {
        // Identity fast path: scan first, only allocate if something would change. Without this, every key
        // duplicates its original even when they're identical, costing ~38 MB per 500k already-lowercase names.
        boolean needsNormalization = false;
        for (int i = 0, len = value.length(); i < len; ) {
            int cp = value.codePointAt(i);
            int normalized = Character.toLowerCase(Character.toUpperCase(cp));
            if (cp != normalized) {
                needsNormalization = true;
                break;
            }
            i += Character.charCount(cp);
        }
        if (!needsNormalization) {
            return value;
        }

        // Something differs; build the normalized copy.
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0, len = value.length(); i < len; ) {
            int cp = value.codePointAt(i);
            sb.appendCodePoint(Character.toLowerCase(Character.toUpperCase(cp)));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    @Override
    public @NonNull String filter(@NonNull String input) {
        // Snapshot once per call so a concurrent reload() can't be observed partway through.
        Snapshot current = snapshot.get();
        String normalized = normalizeCase(input);
        return WordReplacer.replaceWordsByOffset(
                input, normalized, current.pattern(), match -> replacementFor(match, current));
    }

    // An actual match in real content keeps this mapping alive even if the original item is gone -- see
    // ContentMappings#evictStale(). Deliberately not recorded during the pre-fill loop in reload() below, which
    // reads the persisted table without establishing that anything is still live; the NameProvider loop records
    // liveness separately, through getMappingOrCreate. The match is already normalized (it comes from the
    // normalized input), so no derivation is needed. Goes through onMatch rather than touch() because this
    // snapshot may outlive the mapping's presence in the table: a concurrently generated bundle can have
    // evicted it since the reload.
    private static String replacementFor(String match, Snapshot snapshot) {
        ContentMapping mapping = snapshot.matched().get(match);
        if (mapping != null) {
            snapshot.onMatch().accept(mapping);
        }
        return snapshot.replacements().get(match);
    }

    @Override
    public synchronized void reload() {
        final long startTime = System.currentTimeMillis();
        final Map<String, String> replacementsMap = new HashMap<>();
        final Map<String, ContentMapping> matchedMappings = new HashMap<>();
        final WordsTrie trie = new WordsTrie();
        final ContentMappings mappings = ContentMappings.get();
        // The gate below compares the trie key, not the raw original, so stop words need the same derivation.
        // Otherwise two originals that collapse to one key (sap, ſap) are gated differently, the ungated one
        // puts that key in the trie, and the stop word stops working. Sources are mixed -- some lowercase with
        // ENGLISH, some add raw values -- so normalizing here is what makes the check total.
        Set<String> stopWords = mappings.getStopWords().stream()
                .map(SensitiveContentFilter::normalizeCase)
                .collect(Collectors.toSet());

        // Pre-fill with existing mappings (but filter out IPs that is handled by a different filter)
        // This is required to filter out names of items that does not exist anymore, for which they could be record
        // in some content (such as log files that are anonymized when being written)
        StreamSupport.stream(mappings.spliterator(), false)
                // Filter out IP mappings
                .filter(mapping -> !mapping.getReplacement().startsWith("ip_"))
                .forEach(contentMapping -> {
                    String normalizedOriginal = normalizeCase(contentMapping.getOriginal());
                    if (!stopWords.contains(normalizedOriginal)) {
                        replacementsMap.put(normalizedOriginal, contentMapping.getReplacement());
                        matchedMappings.put(normalizedOriginal, contentMapping);
                        trie.add(normalizedOriginal);
                    }
                });

        NameProvider.all()
                .forEach(provider -> provider.names().filter(s -> !s.isBlank()).forEach(name -> {
                    String normalizedOriginal = normalizeCase(name);
                    // NOTE: We could well create a WordTrie for the stop words and use it as a filter instead of the
                    // conditional here. Or find a better way to deal with insensitive key mapping in general.
                    // But the reload is already quite fast anyway. (~1s for 10^4 items with 1 CPU / 2 GB memory
                    // container)
                    if (!stopWords.contains(normalizedOriginal)) {
                        // getMappingOrCreate touches the mapping (refreshes lastSeen) on every call, hit or miss --
                        // that's the "live" signal, since name is something a NameProvider currently reports.
                        ContentMapping mapping = mappings.getMappingOrCreate(
                                name, original -> ContentMapping.of(original, provider.generateFake(original)));
                        replacementsMap.putIfAbsent(normalizedOriginal, mapping.getReplacement());
                        matchedMappings.putIfAbsent(normalizedOriginal, mapping);
                        trie.add(normalizedOriginal);
                    }
                }));
        Pattern pattern = Pattern.compile("(?<!\\w)" + trie.getRegex() + "(?!\\w)");
        this.snapshot.set(new Snapshot(pattern, replacementsMap, matchedMappings, mappings::touchMatched));
        LOGGER.log(Level.FINE, "Took " + (System.currentTimeMillis() - startTime) + "ms to reload");
    }
}
