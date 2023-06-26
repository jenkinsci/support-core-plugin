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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

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

    private final AtomicReference<Pattern> mappingsPattern = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> replacementsMap = new AtomicReference<>();

    public static SensitiveContentFilter get() {
        return ExtensionList.lookupSingleton(SensitiveContentFilter.class);
    }

    @Override
    public @NonNull String filter(@NonNull String input) {
        return WordReplacer.replaceWords(input, mappingsPattern.get(), replacementsMap.get());
    }

    @Override
    public void ensureLoaded() {
        if (mappingsPattern.get() == null || replacementsMap.get() == null) {
            reload();
        }
    }

    @Override
    public synchronized void reload() {
        final long startTime = System.currentTimeMillis();
        final Map<String, String> replacementsMap = new HashMap<>();
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
                    replacementsMap.put(lowerCaseOriginal,
                        contentMapping.getReplacement().replaceAll("\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$"));
                    trie.add(lowerCaseOriginal);
                }
            });

        NameProvider.all().forEach(provider ->
            provider.names()
                .filter(StringUtils::isNotBlank)
                .forEach(name -> {
                    String lowerCaseOriginal = name.toLowerCase(Locale.ENGLISH);
                    // NOTE: We could well create a WordTrie for the stop words and use it as a filter instead of the
                    // conditional here. Or find a better way to deal with insensitive key mapping in general.
                    // But the reload is already quite fast anyway. (~1s for 10^4 items with 1 CPU / 2 GB memory
                    // container)
                    if(!stopWords.contains(lowerCaseOriginal)) {
                        ContentMapping mapping = mappings.getMappingOrCreate(name, original -> ContentMapping.of(original, provider.generateFake()));
                        // Matcher#appendReplacement needs to have the `\` and `$` escaped.
                        replacementsMap.putIfAbsent(lowerCaseOriginal,
                            mapping.getReplacement().replaceAll("\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$"));
                        trie.add(lowerCaseOriginal);
                    }
                }));
        this.mappingsPattern.set(Pattern.compile("(?<!\\w)" + trie.getRegex() + "(?!\\w)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        this.replacementsMap.set(replacementsMap);
        LOGGER.log(Level.FINE, "Took " + (System.currentTimeMillis()-startTime) + "ms to reload");
    }
}
