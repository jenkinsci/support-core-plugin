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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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
    private record Snapshot(Pattern pattern, Map<String, String> replacements, Map<String, ContentMapping> matched) {
        // Matches nothing, so filter() is a no-op before the first reload() rather than risking an NPE.
        private static final Snapshot EMPTY = new Snapshot(Pattern.compile("(?!)"), Map.of(), Map.of());
    }

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    public static SensitiveContentFilter get() {
        return ExtensionList.lookupSingleton(SensitiveContentFilter.class);
    }

    @Override
    public @NonNull String filter(@NonNull String input) {
        // Snapshot once per call so a concurrent reload() can't be observed partway through.
        Snapshot current = snapshot.get();
        return WordReplacer.replaceWords(
                input, current.pattern(), match -> replacementFor(match, current.matched(), current.replacements()));
    }

    // An actual match in real content keeps this mapping alive even if the original item is gone -- see
    // ContentMappings#evictStale(). Deliberately not touched during the pre-fill loop in reload() below, only here,
    // on a real match.
    private static String replacementFor(
            String match, Map<String, ContentMapping> matched, Map<String, String> replacements) {
        String lowerCase = match.toLowerCase(Locale.ENGLISH);
        ContentMapping mapping = matched.get(lowerCase);
        if (mapping != null) {
            mapping.touch();
        }
        return replacements.get(lowerCase);
    }

    @Override
    public synchronized void reload() {
        final long startTime = System.currentTimeMillis();
        final Map<String, String> replacementsMap = new HashMap<>();
        final Map<String, ContentMapping> matchedMappings = new HashMap<>();
        final WordsTrie trie = new WordsTrie();
        final ContentMappings mappings = ContentMappings.get();
        Set<String> stopWords = mappings.getStopWords();

        // Pre-fill with existing mappings (but filter out IPs that is handled by a different filter)
        // This is required to filter out names of items that does not exist anymore, for which they could be record
        // in some content (such as log files that are anonymized when being written)
        StreamSupport.stream(mappings.spliterator(), false)
                // Filter out IP mappings
                .filter(mapping -> !mapping.getReplacement().startsWith("ip_"))
                .forEach(contentMapping -> {
                    String lowerCaseOriginal = contentMapping.getOriginal().toLowerCase(Locale.ENGLISH);
                    if (!stopWords.contains(lowerCaseOriginal)) {
                        replacementsMap.put(
                                lowerCaseOriginal,
                                contentMapping
                                        .getReplacement()
                                        .replaceAll("\\\\", "\\\\\\\\")
                                        .replaceAll("\\$", "\\\\\\$"));
                        matchedMappings.put(lowerCaseOriginal, contentMapping);
                        trie.add(lowerCaseOriginal);
                    }
                });

        NameProvider.all()
                .forEach(provider -> provider.names().filter(s -> !s.isBlank()).forEach(name -> {
                    String lowerCaseOriginal = name.toLowerCase(Locale.ENGLISH);
                    // NOTE: We could well create a WordTrie for the stop words and use it as a filter instead of the
                    // conditional here. Or find a better way to deal with insensitive key mapping in general.
                    // But the reload is already quite fast anyway. (~1s for 10^4 items with 1 CPU / 2 GB memory
                    // container)
                    if (!stopWords.contains(lowerCaseOriginal)) {
                        // getMappingOrCreate touches the mapping (refreshes lastSeen) on every call, hit or miss --
                        // that's the "live" signal, since name is something a NameProvider currently reports.
                        ContentMapping mapping = mappings.getMappingOrCreate(
                                name, original -> ContentMapping.of(original, provider.generateFake()));
                        // Matcher#appendReplacement needs to have the `\` and `$` escaped.
                        replacementsMap.putIfAbsent(
                                lowerCaseOriginal,
                                mapping.getReplacement()
                                        .replaceAll("\\\\", "\\\\\\\\")
                                        .replaceAll("\\$", "\\\\\\$"));
                        matchedMappings.putIfAbsent(lowerCaseOriginal, mapping);
                        trie.add(lowerCaseOriginal);
                    }
                }));
        Pattern pattern = Pattern.compile(
                "(?<!\\w)" + trie.getRegex() + "(?!\\w)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        this.snapshot.set(new Snapshot(pattern, replacementsMap, matchedMappings));
        LOGGER.log(Level.FINE, "Took " + (System.currentTimeMillis() - startTime) + "ms to reload");
    }
}
