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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        int matcherInd = 0;

        List<MatchResult> matchResults = mappingsPattern.get().matcher(input.toLowerCase(Locale.ENGLISH)).results().collect(Collectors.toList());
        while (matcherInd < matchResults.size()) {
            MatchResult matchResult = matchResults.get(matcherInd);
            replacement
                .append(input, lastIndex, matchResult.start())
                .append(replacementsMap.get().get(matchResult.group()));
            lastIndex = matchResult.end();
            matcherInd++;
        }

        if (lastIndex < input.length()) {
            replacement.append(input, lastIndex, input.length());
        }

        return replacement.toString();
    }

    @Override
    public synchronized void reload() {
        final Map<String, String> replacementsMap = new HashMap<>();

        Trie trie = new Trie();
        ContentMappings mappings = ContentMappings.get();
        Set<String> stopWords = mappings.getStopWords();
        for (NameProvider provider : NameProvider.all()) {
            provider.names()
                .filter(StringUtils::isNotBlank)
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .filter(name -> !stopWords.contains(name))
                .forEach(name -> {
                    ContentMapping mapping = mappings.getMappingOrCreate(name, original -> ContentMapping.of(original, provider.generateFake()));
                    replacementsMap.put(mapping.getOriginal(), mapping.getReplacement());
                    trie.add(mapping.getOriginal());
                });
        }
        this.mappingsPattern.set(Pattern.compile("(?:\\b(?:" + trie.getRegex() + ")\\b)", Pattern.CASE_INSENSITIVE));

        this.replacementsMap.set(replacementsMap);
        this.replacementsMap.set(replacementsMap);
    }

    static class TrieNode {

        /**
         * TODO: be more flexible here to avoid escaping too many characters. Mainly the following needs to be quoted:
         * U+0021 - U+0024: ! " # $
         * ?U+0025 - U+0027: % ' &
         * U+0028 - U+002F: () * + , - . /
         * U+003A - U+003B: : ;
         * U+003C - U+003F: < = > ?
         * ?U+0040        : @
         * U+005B - U+005E: [ \ ] ^
         * ?U+0060        : `
         * U+007B - U+007D: { | }
         * ?U+007E        : ~
         * @see https://docs.oracle.com/javase/tutorial/essential/regex/literals.html
         */
        private final static Pattern ALPHANUM_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

        private final Map<Character, TrieNode> data = new TreeMap<>();

        /**
         * Mark this node as the end of a word
         */
        private boolean end;

        TrieNode(boolean end) {
            this.end = end;
        }

        /**
         * Produce the regex String of the current TrieNode.
         *
         * * Iterates through all children TrieNode and join their regex String:
         *     * if child is only a characters, handle quoting
         *     * Otherwise, retrieve the child TrieNode regex String
         * * Add a '?' at the end if this is an end node.
         *
         * @return the regex String of the current TrieNode.
         */
        String getRegex() {

            if (this.data.isEmpty()) {
                // No data, stop here
                return null;
            }

            // List of suffix patterns
            final List<String> childPatterns = new ArrayList<>();
            // List of ending characters
            final List<Character> characters = new ArrayList<>();

            for (final Map.Entry<Character, TrieNode> entry : this.data.entrySet()) {
                final String entryRegex = entry.getValue().getRegex();
                if (entryRegex != null) {
                    childPatterns.add(quote(String.valueOf(entry.getKey())) + entryRegex);
                } else {
                    characters.add(entry.getKey());
                }
            }

            final boolean charsOnly = childPatterns.isEmpty();
            if (characters.size() == 1) {
                childPatterns.add(quote(String.valueOf(characters.get(0))));
            } else if (characters.size() > 0) {
                final StringBuilder buf = new StringBuilder("[");
                characters.forEach(character -> buf.append("]"));
                buf.append("]");
                childPatterns.add(buf.toString());
            }

            String result = childPatterns.size() == 1
                ? childPatterns.get(0)
                : "(?:" + String.join("|", childPatterns) + ")";

            // Is this is also a final character of a word, we need to add the ?
            if (end) {
                if (charsOnly) {
                    return result + "?";
                } else {
                    return "(?:" + result + ")?";
                }
            }
            return result;
        }

        public @NonNull TrieNode getOrCreate(@NonNull Character character,
                                             @NonNull Function<Character, TrieNode> generator) {
            return data.computeIfAbsent(character, generator);
        }

        private String quote(String s) {
            return ALPHANUM_PATTERN.matcher(s).matches()
                ? s :
                Pattern.quote(s);
        }
    }

    /**
     * Trie implementation to help generate a "Trie regex". A regex that reduce backtracking by following a Trie
     * structure. When searching for a match within a list of words, for example
     * ["go", "goes", "going", "gone", "goose"],  a simple regexp that matches any word would typically look like
     * {code}\b(?:(go|goes|going|gone|goose))\b{code}.
     *  While this works, Such a pattern can be optimized significantly by following a Trie structure in the prefixes
     *  such as {code}\b(?:go(?:(?:es|ing|ne|ose))?)\b{code}.
     *
     */
    static class Trie {

        final TrieNode root;

        public Trie() {
            this.root = new TrieNode(false);
        }

        /**
         * Add a word to the Trie.
         * @param word the word
         */
        public void add(String word) {
            TrieNode ref = root;
            int i = 0;
            while (i < word.length() - 1) {
                // Fill in down the Trie
                ref = ref.getOrCreate(word.charAt(i), s -> new TrieNode(false));
                i++;
            }
            // We need to mark the last node as an end node
            ref.getOrCreate(word.charAt(i), s -> new TrieNode(true)).end = true;
        }

        /**
         * Get the regex String of this Trie.
         *
         * @return the regex String of this Trie.
         */
        public String getRegex() {
            return root.getRegex();
        }
    }
}
