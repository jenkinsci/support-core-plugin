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
import hudson.Extension;
import hudson.ExtensionList;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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

    private final ThreadLocal<Pattern> mappingsPattern = new ThreadLocal<>();
    private final ThreadLocal<Map<String, String>> replacementsMap = new ThreadLocal<>();

    public static SensitiveContentFilter get() {
        return ExtensionList.lookupSingleton(SensitiveContentFilter.class);
    }

    @Override
    public @NonNull String filter(@NonNull String input) {
        StringBuilder replacement = new StringBuilder();
        int lastIndex = 0;

        Matcher matcher = mappingsPattern.get().matcher(input.toLowerCase(Locale.ENGLISH));
        Map<String, String> replacements = replacementsMap.get();

        while (matcher.find()) {
            replacement.append(input, lastIndex, matcher.start());
            replacement.append(replacements.get(matcher.group()));
            lastIndex = matcher.end();
        }

        if (lastIndex < input.length()) {
            replacement.append(input, lastIndex, input.length());
        }

        return replacement.toString();
    }

    @Override
    public synchronized void reload() {
        final Map<String, String> replacementsMap = new HashMap<>();
        final WordTrie trie = new WordTrie();
        final ContentMappings mappings = ContentMappings.get();
        Set<String> stopWords = mappings.getStopWords();
        NameProvider.all().forEach(provider ->
            provider.names()
                .filter(StringUtils::isNotBlank)
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .filter(name -> !stopWords.contains(name))
                .forEach(name -> {
                    ContentMapping mapping = mappings.getMappingOrCreate(name, original -> ContentMapping.of(original, provider.generateFake()));
                    replacementsMap.put(mapping.getOriginal(), mapping.getReplacement());
                    trie.add(mapping.getOriginal());
                }));
        this.mappingsPattern.set(Pattern.compile("(?:\\b(?:" + trie.getRegex() + ")\\b)", Pattern.CASE_INSENSITIVE));
        this.replacementsMap.set(replacementsMap);
        this.replacementsMap.set(replacementsMap);
    }
}
