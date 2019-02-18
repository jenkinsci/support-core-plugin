package com.cloudbees.jenkins.support.util;

import java.util.Locale;

public class WordReplacer {
    /**
     * Replace all words in the input by the replaces. The replacements happens only if the texts to replace are not
     * part of a greater word, that is, if the text is a whole word, separated by non {@link Character#isLetterOrDigit(char)}
     * characters.
     * @param input the text where the replacements take place
     * @param words the words to look for and replace
     * @param replaces the new words to use
     */
    public static String replaceWords(String input, String[] words, String[] replaces) {
        StringBuilder sb = new StringBuilder(input);
        replaceWords(sb, words, replaces);
        return sb.toString();
    }

    /**
     * Replace all words in the input by the replaces. The replacements happens only if the texts to replace are not
     * part of a greater word, that is, if the text is a whole word, separated by non {@link Character#isLetterOrDigit(char)}
     * characters.
     * @param input the text where the replacements take place
     * @param words the words to look for and replace
     * @param replaces the new words to use
     */
    public static String replaceWordsIgnoreCase(String input, String[] words, String[] replaces) {
        StringBuilder sb = new StringBuilder(input);
        replaceWordsIgnoreCase(sb, words, replaces);
        return sb.toString();
    }

    /**
     * See {@link #replaceWords(String, String[], String[])}
     * @param input the text where the replacements take place
     * @param words the words to look for and replace
     * @param replaces the new words to use
     */
    public static void replaceWords(StringBuilder input, String[] words, String[] replaces) {
        replaceWords(input, words, replaces, false);
    }

    /**
     * See {@link #replaceWords(String, String[], String[])}
     * @param input the text where the replacements take place
     * @param words the words to look for and replace
     * @param replaces the new words to use
     */
    public static void replaceWordsIgnoreCase(StringBuilder input, String[] words, String[] replaces) {
        replaceWords(input, words, replaces, true);
    }

    // To avoid repeat this code above
    private static void replaceWords(StringBuilder input, String[] words, String[] replaces, boolean ignoreCase) {
        if (input == null || input.length()==0 || words == null ||
                words.length == 0 || replaces == null || replaces.length == 0) {
            return;
        }

        // the same number of word to replace and replaces to use
        if (words.length != replaces.length) {
            throw new IllegalArgumentException(String.format("Words (%d) and replaces (%d) lengths should be equals", words.length, replaces.length));
        }

        for(int i = 0; i < words.length; i++) {
            replaceWord(input, words[i], replaces[i], ignoreCase);
        }
    }

    /**
     * Replace all occurrences of word by replace in the input. The replacement happens only if the word to replace is
     * separated from others words, that is, it's not part of a word. The implementation is that the previous and next
     * characters of the word must be a {@link Character#isLetterOrDigit(char)} char.
     * @param input text where the replacements take place
     * @param word the word to replace
     * @param replace the new text to use
     */
    public static String replaceWord(String input, String word, String replace) {
        StringBuilder sb = new StringBuilder(input);
        replaceWord(sb, word, replace);
        return sb.toString();
    }

    /**
     * Replace all occurrences of word by replace in the input. The replacement happens only if the word to replace is
     * separated from others words, that is, it's not part of a word. The implementation is that the previous and next
     * characters of the word must be a {@link Character#isLetterOrDigit(char)} char.
     * @param input text where the replacements take place
     * @param word the word to replace
     * @param replace the new text to use
     */
    public static String replaceWordIgnoreCase(String input, String word, String replace) {
        StringBuilder sb = new StringBuilder(input);
        replaceWordIgnoreCase(sb, word, replace);
        return sb.toString();
    }

    /**
     * Replace all occurrences of word by replace in the input. The replacement happens only if the word to replace is
     * separated from others words, that is, it's not part of a word. The implementation is that the previous and next
     * characters of the word must be a {@link Character#isLetterOrDigit(char)} char.
     * @param input text where the replacements take place
     * @param word the word to replace
     * @param replace the new text to use
     */
    public static void replaceWord(StringBuilder input, String word, String replace) {
        replaceWord(input, word, replace, false);
    }


    public static void replaceWordIgnoreCase(StringBuilder input, String word, String replace ) {
        replaceWord(input, word, replace, true);
    }

    // Main method
    private static void replaceWord(StringBuilder input, String word, String replace, boolean ignoreCase) {
        // The string where we look for (lower case if ignoreCase)
        StringBuilder workInput;
        // The string to find (lower case if ignoreCase)
        String workWord;

        if (input == null || word == null || input.length() == 0 || word.length() == 0) {
            return;
        }

        if (replace == null) {
            replace = "";
        }

        // Use the lowercase versions to compare, but replace in the original input
        if (ignoreCase) {
            workInput = new StringBuilder(input.toString().toLowerCase(Locale.ENGLISH));
            workWord = word.toLowerCase(Locale.ENGLISH);
        } else {
            // Creates a StringBuilder to work with based in the input, we don't touch the input, to do the
            // replaces there. So we use a new StringBuilder, not an assignment.
            workInput = new StringBuilder(input);
            workWord = word;
        }

        int pos = 0;
        do {
            // Search for the word but from the previous found index, to avoid infinite loop if the replace word
            // contains the word
            pos = workInput.indexOf(workWord, pos);

            // If word is not found
            if (pos == -1) {
                break;
            }

            // If the previous char is a word letter, don't replace, look for the next one.
            if (pos > 0) {
                char prevChar = workInput.charAt(pos - 1);
                if (Character.isLetterOrDigit(prevChar)) {
                    pos = pos + workWord.length();
                    continue;
                }
            }

            // If the next char is a word letter, don't replace, look for the next
            int nextPos = pos + workWord.length();
            if (nextPos < workInput.length()) {
                char nextChar = workInput.charAt(nextPos);
                if (Character.isLetterOrDigit(nextChar)) {
                    pos = pos + workWord.length();
                    continue;
                }
            }

            if (pos >= 0 && pos < input.length()) {
                // replace this word but in the original input (without changing the case)
                input.replace(pos, pos + workWord.length(), replace);
                workInput.replace(pos, pos + workWord.length(), replace);
            }

            // move to the next character after the replace word
            pos = pos + replace.length();
        } while (pos > -1 && pos < workInput.length());
    }
}
