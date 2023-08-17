package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Trie implementation to generate a "Trie regex". A regex that reduces backtracking by following a Trie structure.
 * <p>
 * When searching for a match within a list of words, for example {@code ["go", "goes", "going", "gone", "goose"]},  a simple
 * regex that matches any word would typically look like {@code\b(?:(go|goes|going|gone|goose))\b}.
 * <p>
 * While this works, Such a pattern can be optimized significantly by following a Trie structure in the prefixes such
 * as {@code\b(?:go(?:(?:es|ing|ne|ose))?)\b}.
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

    final TrieNode root;

    public WordsTrie() {
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

    static class TrieNode {

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
         *     * if child is only a character, handle quoting
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

            for (Map.Entry<Character, TrieNode> entry : this.data.entrySet()) {
                final String entryRegex = entry.getValue().getRegex();
                if (entryRegex != null) {
                    // Need to escape special / metacharacters
                    childPatterns.add(quote(entry.getKey()) + entryRegex);
                } else {
                    characters.add(entry.getKey());
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
                    for (int i = 0; i < characters.size(); i += chunkSize) {
                        List<Character> charactersChunk =
                                characters.subList(i, Math.min(i + chunkSize, characters.size()));
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

        public @NonNull TrieNode getOrCreate(
                @NonNull Character character, @NonNull Function<Character, TrieNode> generator) {
            return data.computeIfAbsent(character, generator);
        }

        /**
         * Quote the Character passed in if necessary.
         *
         * @param c the Character
         * @return the maybe quoted string
         */
        private String quote(Character c) {
            String charStr = String.valueOf(c);
            return METACHARACTER.matcher(charStr).matches() ? "\\" + c : charStr;
        }

        /**
         * Quote the Character passed in if necessary.
         *
         * @param c the Character
         * @return the maybe quoted string
         */
        private String quoteCharacterClass(Character c) {
            String charStr = String.valueOf(c);
            return METACHARACTER_CHARACTER_CLASS.matcher(charStr).matches() ? "\\" + c : charStr;
        }
    }
}
