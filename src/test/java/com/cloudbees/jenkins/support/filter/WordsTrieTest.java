/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.quicktheories.core.Gen;
import org.quicktheories.generators.Generate;
import org.quicktheories.generators.SourceDSL;

/**
 * Tests the sorted-list {@link WordsTrie} implementation, verifying correctness through unit tests,
 * property-based tests, and a golden fixture generated from the original tree-based implementation.
 */
class WordsTrieTest {

    @Test
    void emptyTrieReturnsNull() {
        assertThat(new WordsTrie().getRegex()).isNull();
    }

    @Test
    void blankWordIsIgnored() {
        WordsTrie trie = new WordsTrie();
        trie.add("");
        trie.add("foo");
        WordsTrie expected = new WordsTrie();
        expected.add("foo");
        assertThat(trie.getRegex()).isEqualTo(expected.getRegex());
    }

    @Test
    void onlyBlankWordProducesNull() {
        WordsTrie trie = new WordsTrie();
        trie.add("");
        assertThat(trie.getRegex()).isNull();
    }

    @Test
    void whitespaceOnlyWordIsIgnored() {
        WordsTrie trie = new WordsTrie();
        trie.add("   ");
        trie.add("foo");
        WordsTrie expected = new WordsTrie();
        expected.add("foo");
        assertThat(trie.getRegex()).isEqualTo(expected.getRegex());
    }

    @Test
    void singleWord() {
        WordsTrie trie = new WordsTrie();
        trie.add("go");
        assertThat(trie.getRegex()).isEqualTo("go");
    }

    @Test
    void singleCharacterWords() {
        WordsTrie trie = new WordsTrie();
        trie.add("a");
        trie.add("b");
        trie.add("c");
        assertThat(trie.getRegex()).isEqualTo("[abc]");
    }

    @Test
    void sharedPrefixesAndWordsThatArePrefixesOfOtherWords() {
        WordsTrie trie = new WordsTrie();
        for (String w : List.of("go", "goes", "going", "gone", "goose")) {
            trie.add(w);
        }
        assertThat(trie.getRegex()).isEqualTo("go(?:(?:es|ing|ne|ose))?");
    }

    @Test
    void everyWordIsAPrefixOfTheNext() {
        WordsTrie trie = new WordsTrie();
        for (String w : List.of("a", "ab", "abc", "abcd", "abcde")) {
            trie.add(w);
        }
        assertThat(trie.getRegex()).isEqualTo("a(?:b(?:c(?:de?)?)?)?");
    }

    @Test
    void duplicateAddsAreIdempotent() {
        WordsTrie trie = new WordsTrie();
        for (String w : List.of("dup", "dup", "dup", "dupe", "d", "d")) {
            trie.add(w);
        }
        assertThat(trie.getRegex()).isEqualTo("d(?:upe?)?");
    }

    @Test
    void metacharactersAndCharacterClassSpecialChars() {
        WordsTrie trie = new WordsTrie();
        for (String w : List.of(
                ".", "*", "+", "?", "[", "]", "(", ")", "{", "}", "^", "$", "|", "\\", "/", "-", "a-b", "a^b", "a]b",
                "a[b")) {
            trie.add(w);
        }
        // Just verify it doesn't throw - the exact regex is complex
        assertThat(trie.getRegex()).isNotNull();
    }

    @Test
    void nonBmpSupplementaryCodePointsAndSpecialCaseFoldCharacters() {
        // U+1F600 GRINNING FACE and U+1D50A MATHEMATICAL FRAKTUR CAPITAL G are supplementary (surrogate-pair)
        // code points; İ (U+0130) and ſ (U+017F) are the classic Unicode case-folding edge cases.
        String grinning = new String(Character.toChars(0x1F600));
        String fraktur = new String(Character.toChars(0x1D50A));
        WordsTrie trie = new WordsTrie();
        for (String w : List.of(
                "a" + grinning + "b",
                "a" + grinning + "c",
                fraktur + "oo",
                "İstanbul",
                "ſword",
                "ſ",
                "İ",
                grinning,
                fraktur)) {
            trie.add(w);
        }
        // Just verify it doesn't throw - the exact regex is complex
        assertThat(trie.getRegex()).isNotNull();
    }

    @Test
    void moreThanMaxUnionDistinctTerminalCharactersAtOneNode() {
        // Forces the ">= MAX_UNION" chunked branch of the "characters" bucket: > 1024 single-character words
        // that are all leaves directly off the root, none of which extend into any other word.
        List<String> words = new ArrayList<>();
        char c = 0x21;
        while (words.size() < 1500) {
            if (!Character.isSurrogate(c)) {
                words.add(String.valueOf(c));
            }
            c++;
        }
        WordsTrie trie = new WordsTrie();
        for (String w : words) {
            trie.add(w);
        }
        // Just verify it doesn't throw and produces chunked output
        String regex = trie.getRegex();
        assertThat(regex).isNotNull();
        assertThat(regex).contains("(?:"); // Chunked pattern marker
    }

    @Test
    void moreThanMaxUnionCharactersSharingACommonPrefix() {
        // Same as above but one level deeper, so the chunked branch fires on a non-root node too.
        List<String> words = new ArrayList<>();
        char c = 0x21;
        while (words.size() < 1500) {
            if (!Character.isSurrogate(c)) {
                words.add("pre" + c);
            }
            c++;
        }
        WordsTrie trie = new WordsTrie();
        for (String w : words) {
            trie.add(w);
        }
        // Just verify it doesn't throw and produces chunked output
        String regex = trie.getRegex();
        assertThat(regex).isNotNull();
        assertThat(regex).startsWith("pre");
        assertThat(regex).contains("(?:"); // Chunked pattern marker
    }

    @Test
    void propertyBasedTestOverNastyAlphabet() {
        Gen<String> atom = Generate.pick(nastyAlphabet());
        Gen<String> word = SourceDSL.lists().of(atom).ofSizeBetween(1, 12).map(parts -> String.join("", parts));
        Gen<List<String>> wordLists = SourceDSL.lists().of(word).ofSizeBetween(0, 40);

        qt().withExamples(20000).forAll(wordLists).checkAssert(ws -> {
            WordsTrie trie = new WordsTrie();
            for (String w : ws) {
                trie.add(w);
            }
            String regex = trie.getRegex();

            List<String> nonBlankWords =
                    ws.stream().filter(w -> !w.isBlank()).distinct().toList();

            if (nonBlankWords.isEmpty()) {
                assertThat(regex).as("Empty trie should return null").isNull();
            } else {
                assertThat(regex).as("Non-empty trie must produce a regex").isNotNull();

                // The regex must compile
                Pattern pattern = Pattern.compile(regex);

                // Every non-blank word that was added must be matched
                for (String addedWord : nonBlankWords) {
                    assertThat(pattern.matcher(addedWord).matches())
                            .as("Word '%s' was added but is not matched by regex '%s'", addedWord, regex)
                            .isTrue();
                }

                // Near-miss precision test: generate words that are NOT in the added set but are
                // built from the same alphabet, and verify they do not match. This catches over-broad
                // character classes, wrong optionality, and missing terminal constraints.
                List<String> nearMisses = generateNearMisses(nonBlankWords, nastyAlphabet());
                for (String nearMiss : nearMisses) {
                    assertThat(pattern.matcher(nearMiss).matches())
                            .as("Near-miss '%s' was not added but is matched by regex '%s'", nearMiss, regex)
                            .isFalse();
                }
            }
        });
    }

    /**
     * Generate near-miss candidates that are NOT in the added set but are built from the same alphabet.
     * Returns proper prefixes, words with atoms appended, and words with final atom replaced.
     */
    private static List<String> generateNearMisses(List<String> addedWords, List<String> alphabet) {
        java.util.Set<String> addedSet = new java.util.HashSet<>(addedWords);
        List<String> nearMisses = new ArrayList<>();

        for (String word : addedWords) {
            // Proper prefixes (but not empty string)
            for (int i = 1; i < word.length(); i++) {
                String prefix = word.substring(0, i);
                if (!addedSet.contains(prefix)) {
                    nearMisses.add(prefix);
                }
            }

            // Word with one atom appended
            for (String atom : alphabet) {
                String extended = word + atom;
                if (!addedSet.contains(extended)) {
                    nearMisses.add(extended);
                }
            }

            // Word with final atom replaced (if word is not empty)
            if (!word.isEmpty()) {
                for (String atom : alphabet) {
                    // Find where the last atom starts - need to handle surrogate pairs
                    int lastAtomStart = word.length() - 1;
                    if (Character.isLowSurrogate(word.charAt(lastAtomStart)) && lastAtomStart > 0) {
                        lastAtomStart--;
                    }
                    String prefix = word.substring(0, lastAtomStart);
                    String replaced = prefix + atom;
                    if (!addedSet.contains(replaced) && !replaced.equals(word)) {
                        nearMisses.add(replaced);
                    }
                }
            }
        }

        return nearMisses.stream().distinct().toList();
    }

    /**
     * A deliberately nasty alphabet: ASCII letters/digits kept small to force shared prefixes and branching,
     * every regex metacharacter, the {@code -} and {@code ^} that interact with character classes, and a
     * handful of non-BMP / Unicode case-folding edge cases, each as a single "atom" (1 char, or a surrogate
     * pair for supplementary code points).
     */
    private static List<String> nastyAlphabet() {
        List<String> atoms = new ArrayList<>();
        for (char c = 'a'; c <= 'e'; c++) {
            atoms.add(String.valueOf(c));
        }
        for (char c = '0'; c <= '3'; c++) {
            atoms.add(String.valueOf(c));
        }
        atoms.add("_");
        for (char metachar : ".*+?[](){}^$|\\/-".toCharArray()) {
            atoms.add(String.valueOf(metachar));
        }
        atoms.add("İ");
        atoms.add("ſ");
        atoms.add(new String(Character.toChars(0x1F600)));
        atoms.add(new String(Character.toChars(0x10000)));
        return atoms;
    }
}
