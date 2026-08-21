package com.cloudbees.jenkins.support.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generates a "Trie regex" for a set of words. A regex that reduces backtracking by following a Trie structure.
 * <p>
 * When searching for a match within a list of words, for example {@code ["go", "goes", "going", "gone", "goose"]},  a simple
 * regex that matches any word would typically look like {@code\b(?:(go|goes|going|gone|goose))\b}.
 * <p>
 * While this works, Such a pattern can be optimized significantly by following a Trie structure in the prefixes such
 * as {@code\b(?:go(?:(?:es|ing|ne|ose))?)\b}.
 * <p>
 * Rather than materializing an actual Trie of nodes (which, for large word sets, costs many times more memory than
 * the regex it produces), the words are sorted and the same grouping the Trie would have produced is recovered by
 * walking contiguous ranges of the sorted list: words sharing a prefix are adjacent once sorted, so each "node" of
 * the conceptual Trie corresponds to a range of the sorted array, and children correspond to sub-ranges grouped by
 * their next character - which, thanks to natural {@link String} ordering, are visited in the same ascending
 * character order a {@code TreeMap<Character, ...>}-based Trie would have visited them in.
 */
public class WordsTrie {

    /*
     * The maximum number of union in Character classes. For example [abcde.....] would result in stackoverflow
     * due to a recursive process in Pattern.
     */
    private static final int MAX_UNION = 1024;

    /*
     * As per https://docs.oracle.com/javase/tutorial/essential/regex/literals.html, metacharacters need to be escaped
     * in the regex String. The regular expression matches any metacharacter occurrence in a String, in which case
     * the String will need to be quoted.
     *
     * NOTE: We could instead choose to {@link Pattern#quote} and not do that check. This would increase the size of the
     * regex String. This does seem to have a significant impact on performance. So escaping only those characters for
     * now.
     */
    private static final Pattern METACHARACTER =
            Pattern.compile("[\\x21\\x24\\x28-\\x2B\\x2D-\\x2F\\x3C-\\x3F\\x5B-\\x5E\\x7B-\\x7D]+");
    /*
     * For Character Class, only / - [ ] ^ \ must be escaped.
     */
    private static final Pattern METACHARACTER_CHARACTER_CLASS = Pattern.compile("[\\x2D\\x2F\\x5B-\\x5E]+");

    private final List<String> words = new ArrayList<>();

    public WordsTrie() {}

    /**
     * Add a word to the Trie.
     * @param word the word
     */
    public void add(String word) {
        if (word.isBlank()) {
            return;
        }
        words.add(word);
    }

    /**
     * Get the regex String of this Trie.
     *
     * @return the regex String of this Trie.
     */
    public String getRegex() {
        if (words.isEmpty()) {
            // No data, stop here
            return null;
        }
        String[] sorted = words.toArray(String[]::new);
        // Natural String ordering compares characters one at a time using their numeric value, exactly like
        // Character's natural ordering does - so sorting here reproduces the ascending per-character grouping a
        // TreeMap<Character, TrieNode>-based Trie would visit its children in.
        Arrays.sort(sorted);
        int distinct = dedupe(sorted);
        return regexForRange(sorted, 0, distinct, 0);
    }

    /**
     * Compacts a sorted array in place, removing adjacent duplicates.
     *
     * @return the number of distinct leading elements after compaction
     */
    private static int dedupe(String[] sorted) {
        int distinct = 0;
        for (String s : sorted) {
            if (distinct == 0 || !s.equals(sorted[distinct - 1])) {
                sorted[distinct++] = s;
            }
        }
        return distinct;
    }

    /**
     * Produce the regex String for the conceptual TrieNode reached by the common prefix of length {@code depth}
     * shared by every word in {@code words[lo, hi)}.
     * <p>
     * The caller guarantees {@code lo < hi}, and that this range is not a "pure leaf" (see below) - i.e. that the
     * conceptual TrieNode has at least one child, exactly as a real TrieNode's {@code data} map would.
     *
     * @return the regex String of the current node. Never {@code null}: the empty-Trie case is handled by the
     * caller, and every recursive call below is only made on ranges that are known to have at least one child.
     */
    private static String regexForRange(String[] words, int lo, int hi, int depth) {
        // A word ending exactly at this depth is this node's "end" marker; since words are distinct, at most one
        // such word can be in the range, and - being a prefix of every other word here - it sorts first.
        boolean end = false;
        int contentLo = lo;
        if (words[lo].length() == depth) {
            end = true;
            contentLo = lo + 1;
        }

        // List of suffix patterns
        final List<String> childPatterns = new ArrayList<>();
        // List of ending characters
        final List<Character> characters = new ArrayList<>();

        int i = contentLo;
        while (i < hi) {
            char c = words[i].charAt(depth);
            int start = i;
            while (i < hi && words[i].charAt(depth) == c) {
                i++;
            }
            // A pure leaf child (no word extends past it) is exactly one word of length depth+1 in this run.
            if (i - start == 1 && words[start].length() == depth + 1) {
                characters.add(c);
            } else {
                // Need to escape special / metacharacters
                childPatterns.add(quote(c) + regexForRange(words, start, i, depth + 1));
            }
        }

        final boolean charsOnly = childPatterns.isEmpty();
        if (characters.size() == 1) {
            // Need to escape special / metacharacters
            childPatterns.add(quote(characters.get(0)));
        } else if (characters.size() > 0) {
            // Chunking is necessary here to prevent StackOverFlow in pattern matching unions
            final StringBuilder buf = new StringBuilder();
            if (characters.size() < MAX_UNION) {
                buf.append("[");
                characters.forEach(character -> buf.append(quote(character)));
                buf.append("]");
            } else {
                buf.append("(?:");
                int chunkSize = MAX_UNION;
                for (int j = 0; j < characters.size(); j += chunkSize) {
                    List<Character> charactersChunk = characters.subList(j, Math.min(j + chunkSize, characters.size()));
                    buf.append('[');
                    for (Character character : charactersChunk) {
                        buf.append(quoteCharacterClass(character));
                    }
                    buf.append("]|");
                }
                buf.deleteCharAt(buf.length() - 1);
                buf.append(')');
            }
            childPatterns.add(buf.toString());
        }

        String result =
                childPatterns.size() == 1 ? childPatterns.get(0) : "(?:" + String.join("|", childPatterns) + ")";

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

    /**
     * Quote the Character passed in if necessary.
     *
     * @param c the Character
     * @return the maybe quoted string
     */
    private static String quote(Character c) {
        String charStr = String.valueOf(c);
        return METACHARACTER.matcher(charStr).matches() ? "\\" + c : charStr;
    }

    /**
     * Quote the Character passed in if necessary.
     *
     * @param c the Character
     * @return the maybe quoted string
     */
    private static String quoteCharacterClass(Character c) {
        String charStr = String.valueOf(c);
        return METACHARACTER_CHARACTER_CLASS.matcher(charStr).matches() ? "\\" + c : charStr;
    }
}
