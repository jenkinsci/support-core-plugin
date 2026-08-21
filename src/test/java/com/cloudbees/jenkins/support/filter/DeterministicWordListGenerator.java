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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a deterministic word list for golden fixture testing.
 * Uses a fixed seed to ensure reproducibility across test runs and JVM versions.
 */
class DeterministicWordListGenerator {

    private static final long SEED = 0xDEADBEEF;

    /**
     * The nasty alphabet: ASCII letters/digits kept small to force shared prefixes,
     * every regex metacharacter, - and ^ that interact with character classes,
     * and surrogate pairs for non-BMP code points.
     */
    private static final List<String> NASTY_ALPHABET = List.of(
            "a",
            "b",
            "c",
            "d",
            "e",
            "0",
            "1",
            "2",
            "3",
            "_",
            ".",
            "*",
            "+",
            "?",
            "[",
            "]",
            "(",
            ")",
            "{",
            "}",
            "^",
            "$",
            "|",
            "\\",
            "/",
            "-",
            "İ",
            "ſ",
            new String(Character.toChars(0x1F600)), // GRINNING FACE
            new String(Character.toChars(0x10000)) // LINEAR B SYLLABLE B008 A
            );

    /**
     * Generate a large deterministic word list using the default seed.
     * @return list of words with shared prefixes, duplicates, metacharacters, and surrogate pairs
     */
    static List<String> generate() {
        Random rng = new Random(SEED);
        List<String> words = new ArrayList<>();

        // Generate words of varying lengths to force prefix-of-prefix chains
        for (int i = 0; i < 10000; i++) {
            int wordLength = 1 + rng.nextInt(8); // 1-8 atoms per word
            StringBuilder word = new StringBuilder();
            for (int j = 0; j < wordLength; j++) {
                word.append(NASTY_ALPHABET.get(rng.nextInt(NASTY_ALPHABET.size())));
            }
            words.add(word.toString());
        }

        // Add some duplicates
        for (int i = 0; i < 100; i++) {
            words.add(words.get(rng.nextInt(words.size())));
        }

        return words;
    }

    /**
     * Generate a deterministic word list using a specific seed.
     * Word count and length vary by seed to exercise different trie shapes.
     * @param seed the random seed
     * @return list of words with shared prefixes, duplicates, metacharacters, and surrogate pairs
     */
    static List<String> generateWithSeed(long seed) {
        Random rng = new Random(seed);
        List<String> words = new ArrayList<>();

        // Vary word count by seed to exercise different trie sizes
        int wordCount = 100 + rng.nextInt(10000);
        for (int i = 0; i < wordCount; i++) {
            int wordLength = 1 + rng.nextInt(8); // 1-8 atoms per word
            StringBuilder word = new StringBuilder();
            for (int j = 0; j < wordLength; j++) {
                word.append(NASTY_ALPHABET.get(rng.nextInt(NASTY_ALPHABET.size())));
            }
            words.add(word.toString());
        }

        // Add some duplicates
        int dupeCount = Math.min(100, words.size() / 10);
        for (int i = 0; i < dupeCount; i++) {
            words.add(words.get(rng.nextInt(words.size())));
        }

        return words;
    }
}
