package com.cloudbees.jenkins.support.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.filter.WordsTrie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class WordReplacerTest {

    @Test
    void wordReplacerTest() {
        // The ignore case doesn't work in these cases:
        // Chinese numbers lower: 一, 二,三,四,五,六,七,八,九,十,百,千,萬
        // Chinese numbers upper: 壹,貳,參,肆,伍,陸,柒,捌,玖,拾,佰,仟,萬

        String input =
                "abcdefghijklmnñopqrstuvwxyz Beijing: 佩 One: 一, CasaBlanca: الدار البيضاء Casa Blanca. Numbers: 一, 二,三,四,五,六,七,八,九,十,百,千,萬";
        String expected =
                "abcdefghijklmnñopqrstuvwxyz *beijing*: *佩* One: 一, CasaBlanca: *الدار البيضاء* Casa *BLANCA*. Numbers: 一, 二,三,四,五,六,七,八,九,十,百,千,萬";

        String[] search = new String[] {"a", "Ñ", "beijing", "佩", "الدار البيضاء", "blaNca", "壹"};
        String[] replace = new String[] {"*a*", "*ñ*", "*beijing*", "*佩*", "*الدار البيضاء*", "*BLANCA*", "*1*"};

        String result = WordReplacer.replaceWords(input, triePattern(search), replacementsMap(search, replace));
        assertEquals(expected, result);
    }

    @Test
    void specialCharacter() {
        // Test the replacement of a single word a*z with * being a set of special characters
        String singleWord = "a~`!@#$%^&*()_+-={}[]|\\:\";'<>?,./z";
        String singleWordReplace = "filtered";

        String[] searches = new String[] {singleWord};
        String[] replaces = new String[] {singleWordReplace};

        Pattern triePattern = triePattern(searches);
        Map<String, String> replacementMap = replacementsMap(searches, replaces);

        assertEquals(singleWordReplace, WordReplacer.replaceWords(singleWord, triePattern, replacementMap));

        // Test the replacement of a single word a*z with * being a simple special character
        searches = new String[singleWord.length()];
        replaces = new String[singleWord.length()];
        StringBuilder individualWord = new StringBuilder();
        StringBuilder individualWordReplace = new StringBuilder();
        for (int i = 0; i < singleWord.length(); i++) {
            Character character = singleWord.charAt(i);
            individualWord.append('a').append(character).append("z").append(" ");
            individualWordReplace.append("**").append(character).append("**").append(" ");

            searches[i] = "a" + character + "z";
            replaces[i] = "**" + character + "**";
        }
        individualWord.deleteCharAt(individualWord.length() - 1);
        individualWordReplace.deleteCharAt(individualWordReplace.length() - 1);

        triePattern = triePattern(searches);
        replacementMap = replacementsMap(searches, replaces);

        assertEquals(
                individualWordReplace.toString(),
                WordReplacer.replaceWords(individualWord.toString(), triePattern, replacementMap));
    }

    @Test
    void characterScopeReplaceWordsPattern() {
        // Test the replacement of single words a*z for all characters
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isLetterOrDigit(c)) {
                words.add("a" + c + "z");
                replaceList.add("**" + c + "**");
            }
        }

        Pattern triePattern = triePattern(words.toArray(new String[0]), false);
        Map<String, String> replacementMap =
                replacementsMap(words.toArray(new String[0]), replaceList.toArray(new String[0]), false);

        assertEquals(
                String.join(" ", replaceList),
                WordReplacer.replaceWords(String.join(" ", words), triePattern, replacementMap::get));
    }

    @Test
    void replacementByFunctionTest() {
        String[] words = new String[] {"a", "b", "c"};
        String[] replaceList = new String[] {"1", "2", "3"};

        Map<String, String> replacementMap = replacementsMap(words, replaceList);
        String input = "a A b,B.c:C abc ABC ignored";
        assertEquals(
                "1 1 2,2.3:3 abc ABC ignored",
                WordReplacer.replaceWords(
                        input,
                        triePattern(words),
                        key -> replacementMap.getOrDefault(key.toLowerCase(Locale.ENGLISH), key)));
    }

    @Test
    void replacementByShorterWordTest() {
        String input = "input one input";
        String[] words = new String[] {"input", "one"};
        String[] replaces = new String[] {"i", "o"};

        String result = "i o i";

        assertEquals(result, WordReplacer.replaceWords(input, triePattern(words), replacementsMap(words, replaces)));
    }

    @Test
    @Issue("JENKINS-71529")
    void testBoundaries() {
        String specialChars = "~`!@#$%^&*()_+-={}[]|\\:\";'<>?,./";
        String[] words = new String[specialChars.length()];
        String[] replaces = new String[specialChars.length()];
        for (int i = 0; i < specialChars.length(); i++) {
            words[i] = specialChars.charAt(i) + "word" + specialChars.charAt(i);
            replaces[i] = "**" + words[i] + "**";
        }
        String result = String.join(" ", replaces);

        assertEquals(
                result,
                WordReplacer.replaceWords(
                        String.join(" ", words), triePattern(words), replacementsMap(words, replaces)));
    }

    private static Pattern triePattern(String[] originals, boolean lowercase) {
        WordsTrie trie = new WordsTrie();
        for (String search : originals) {
            trie.add(lowercase ? search.toLowerCase(Locale.ENGLISH) : search);
        }
        return Pattern.compile(
                "(?<!\\w)" + trie.getRegex() + "(?!\\w)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static Pattern triePattern(String[] searches) {
        return triePattern(searches, true);
    }

    private static Map<String, String> replacementsMap(String[] originals, String[] replacements, boolean lowercase) {
        assertTrue(originals.length == replacements.length);
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < originals.length; i++) {
            // Escape replacement strings for Matcher.appendReplacement
            String escaped = replacements[i].replaceAll("\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$");
            map.put(lowercase ? originals[i].toLowerCase(Locale.ENGLISH) : originals[i], escaped);
        }
        return map;
    }

    private static Map<String, String> replacementsMap(String[] searches, String[] replaces) {
        return replacementsMap(searches, replaces, true);
    }
}
