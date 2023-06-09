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
 * When searching for a match within a list of words, for example ["go", "goes", "going", "gone", "goose"],  a simple
 * regex that matches any word would typically look like {code}\b(?:(go|goes|going|gone|goose))\b{code}.
 * <p>
 * While this works, Such a pattern can be optimized significantly by following a Trie structure in the prefixes such
 * as {code}\b(?:go(?:(?:es|ing|ne|ose))?)\b{code}.
 */
public class WordTrie {

    /*
     * As per https://docs.oracle.com/javase/tutorial/essential/regex/literals.html, metacharacters need to be escaped
     * in the regex String. The regular expression matches any metacharacter occurrence in a String, in which case
     * the String will need to be quoted.
     *
     * NOTE: We could instead choose to escape everything and not do that check. This would increase the size of the
     * regex String. This does seem to have a significant impact on performance. So escaping only those characters for
     * now.
     */
    private final static Pattern METACHARACTER =
        Pattern.compile("[\\x21\\x24\\x28-\\x2B\\x2D-\\x2F\\x3C-\\x3F\\x5B-\\x5E\\x7B-\\x7D]+");

    final TrieNode root;

    public WordTrie() {
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

            for (final Map.Entry<Character, TrieNode> entry : this.data.entrySet()) {
                final String entryRegex = entry.getValue().getRegex();
                if (entryRegex != null) {
                    // Need to escape special / metacharacters
                    childPatterns.add(quote(String.valueOf(entry.getKey())) + entryRegex);
                } else {
                    characters.add(entry.getKey());
                }
            }

            final boolean charsOnly = childPatterns.isEmpty();
            if (characters.size() == 1) {
                // Need to escape special / metacharacters
                childPatterns.add(quote(String.valueOf(characters.get(0))));
            } else if (characters.size() > 0) {
                final StringBuilder buf = new StringBuilder("[");
                characters.forEach(buf::append);
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

        /**
         * Quote the String passed in if necessary (i.e. if it is metacharacter).
         *
         * @param s the String
         * @return the maybe quoted string
         */
        private String quote(String s) {
            return METACHARACTER.matcher(s).matches() ? Pattern.quote(s) : s;
        }
    }
}
