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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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

    private final transient ThreadLocal<Pattern> mappingsPattern = new ThreadLocal<>();
    private final transient ThreadLocal<Map<String, String>> replacementsMap = new ThreadLocal<>();

    public static SensitiveContentFilter get() {
        return ExtensionList.lookupSingleton(SensitiveContentFilter.class);
    }

    @Override
    public @NonNull String filter(@NonNull String input) {
        return WordReplacer.replaceWords(input, mappingsPattern.get(), replacementsMap.get());
    }

    @Override
    public synchronized void reload() {
        final long startTime = System.currentTimeMillis();
        final Map<String, String> replacementsMap = new HashMap<>();
        final WordsTrie trie = new WordsTrie();
        final ContentMappings mappings = ContentMappings.get();
        Set<String> stopWords = mappings.getStopWords();
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
                        replacementsMap.put(lowerCaseOriginal, mapping.getReplacement());
                        trie.add(lowerCaseOriginal);
                    }
                }));
        this.mappingsPattern.set(Pattern.compile("\\b" + trie.getRegex() + "\\b", Pattern.CASE_INSENSITIVE));
        this.replacementsMap.set(replacementsMap);
        LOGGER.log(Level.FINE, "Took " + (System.currentTimeMillis()-startTime) + "ms to reload");
    }
}
