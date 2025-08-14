package com.cloudbees.jenkins.support.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.filter.ContentMapping;
import com.cloudbees.jenkins.support.filter.FilteredOutputStreamTest;
import com.cloudbees.jenkins.support.filter.WordsTrie;
import hudson.Functions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.recipes.WithTimeout;

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
        // In an ideal world:
        // String expected = "abcdefghijklmnñopqrstuvwxyz *beijing*: *佩* One: *1*, CasaBlanca: *الدار البيضاء* Casa
        // *BLANCA*. Numbers: *1*, 二,三,四,五,六,七,八,九,十,百,千,萬";

        String[] search = new String[] {"a", "Ñ", "beijing", "佩", "الدار البيضاء", "blaNca", "壹"};
        String[] replace = new String[] {"*a*", "*ñ*", "*beijing*", "*佩*", "*الدار البيضاء*", "*BLANCA*", "*1*"};

        String result = WordReplacer.replaceWordsIgnoreCase(input, search, replace);
        System.out.format("%s ->\n%s", input, result);
        assertEquals(expected, result);

        result = WordReplacer.replaceWords(input, triePattern(search), replacementsMap(search, replace));
        assertEquals(expected, result);
    }

    @Test
    void sameResultTest() {
        String[] searches = new String[] {"one", "two"};
        String[] replaces = new String[] {"111", "111"};

        // Populate the content mapping objects to filter
        ContentMapping[] contentMappings = getContentMappings(searches, replaces);

        // Texts to test against
        // regexp doesn't replace ;word, we do. It's even better but we can't check it
        String[] inputs = new String[] {
            "",
            "a",
            "none",
            "one should be replaced",
            "onecar shouldn't be replaced",
            "two is replaced",
            "must replace two",
            "shouldn't replace twoo",
            "one!one,one!onetwoone_twoone#one?one:one->one\"one",
            "'one|twoone, \"one\\two one\ntwo one\ttwo one=two (one+two*one) all replaced ",
            "one\\two one\ntwo one\ttwo one=two (one+two*one) all replaced "
        };

        // For each text, filter using ContentMapping and with WordReplacer as well
        for (String input : inputs) {
            String resultCM = input;
            for (ContentMapping cm : contentMappings) {
                resultCM = cm.filter(resultCM);
            }

            String resultWordReplacer = WordReplacer.replaceWordsIgnoreCase(input, searches, replaces);

            // Both filtered strings should be the same
            assertEquals(
                    resultCM,
                    resultWordReplacer,
                    "The string replaced should be the same using RegExp or WordReplacer");

            resultWordReplacer =
                    WordReplacer.replaceWords(input, triePattern(searches), replacementsMap(searches, replaces));

            // Both filtered strings should be the same
            assertEquals(
                    resultCM,
                    resultWordReplacer,
                    "The string replaced should be the same using RegExp or WordReplacer");
        }
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

        assertEquals(singleWordReplace, WordReplacer.replaceWords(singleWord, searches, replaces));
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
                WordReplacer.replaceWords(individualWord.toString(), searches, replaces));
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
    @WithTimeout(120)
    void characterScopeReplaceWordsPatternIgnoreCase() {
        // Test the replacement of single words a*z for all characters
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isLetterOrDigit(c)) {
                words.add("a" + c + "z");
                replaceList.add(("**" + c + "**").toLowerCase(Locale.ENGLISH));
            }
        }

        Pattern triePattern = triePattern(words.toArray(new String[0]));
        Map<String, String> replacementMap =
                replacementsMap(words.toArray(new String[0]), replaceList.toArray(new String[0]));

        assertEquals(
                String.join(" ", replaceList),
                WordReplacer.replaceWords(
                        String.join(" ", words), triePattern, s -> replacementMap.get(s.toLowerCase(Locale.ENGLISH))));
    }

    @Test
    @WithTimeout(120)
    void characterScopeReplaceWords() {
        // Test the replacement of single words a*z for all characters
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isLetterOrDigit(c)) {
                words.add("a" + c + "z");
                replaceList.add("**" + c + "**");
            }
        }
        String[] searches = words.toArray(new String[0]);
        String[] replaces = replaceList.toArray(new String[0]);
        assertEquals(
                String.join(" ", replaceList), WordReplacer.replaceWords(String.join(" ", words), searches, replaces));
    }

    @Disabled("This test takes a very long time but we keep it here for future cases")
    @Test
    void characterScopeReplaceWordsIgnoreCase() {
        // Test the replacement of single words a*z for all characters
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isLetterOrDigit(c)) {
                words.add("a" + c + "z");
                replaceList.add("**" + c + "**");
            }
        }
        String[] searches = words.toArray(new String[0]);
        String[] replaces = replaceList.toArray(new String[0]);
        assertEquals(
                String.join(" ", replaceList),
                WordReplacer.replaceWordsIgnoreCase(String.join(" ", words), searches, replaces));
    }

    @Disabled("It was useful to make the decision to move out of ContentMapping#filter and later out of WordReplacer "
            + "without Pattern. It has no sense to test. We keep it here for future cases.")
    @Test
    void performanceTest() {
        // Create a lot of word and replaces (each character letter or digit. Aprox: 4070)
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isLetterOrDigit(c)) {
                words.add(String.valueOf(c));
                replaceList.add("**" + c + "**");
            }
        }

        String[] searches = words.toArray(new String[0]);
        String[] replaces = replaceList.toArray(new String[0]);

        // Generate a fake long text (well, not so long to avoid lasts too much)
        List<String> text = generateFakeListString(10);
        // Result with 100 lines in i7
        // Point 'ContentMappings': 1.785 seconds since last point
        // Point 'WordReplacer': 6.114 seconds since last point
        // Point 'TrieRegexPattern': 649.0 milliseconds since last point

        // Create the content mappings
        ContentMapping[] contentMappings = getContentMappings(searches, replaces);

        // Create the searches and replaces for the WordReplacer mimicking the behavior of the Content Mapping
        String[][][] tokens = getWordsLikeContentMapping(searches, replaces);

        // Create the Trie pattern and replacements map mimicking the behavior of the Content Mapping
        Pattern triePattern = triePattern(searches);
        Map<String, String> replacementMap = getReplacementsLikeContentMapping(tokens);

        Chrono c = new Chrono("Test ContentMapping vs WordReplacer vs TrieRegexPattern");
        // Filter using WordReplacer in the same way as ContentMapping
        for (String line : text) {
            String resultCM = line;
            for (ContentMapping cm : contentMappings) {
                resultCM = cm.filter(resultCM);
            }
        }
        c.markFromPrevious("ContentMappings");

        // Filter using WordReplacer in the same way as ContentMapping
        for (String line : text) {
            String resultWR = line;
            for (String[][] token : tokens) {
                resultWR = WordReplacer.replaceWordsIgnoreCase(resultWR, token[0], token[1]);
            }
            //            WordReplacer.replaceWordsIgnoreCase(line, searches, replaces);
        }
        c.markFromPrevious("WordReplacer");

        Pattern pattern2 = unionPattern(searches);

        // Filter using TrieRegex pattern
        for (String line : text) {
            WordReplacer.replaceWords(line, pattern2, replacementMap);
        }
        c.markFromPrevious("RegexPatterns");

        // Filter using TrieRegex pattern
        for (String line : text) {
            WordReplacer.replaceWords(line, triePattern, replacementMap);
        }
        c.markFromPrevious("TrieRegexPattern");

        System.out.print(c.printMeasure("ContentMappings"));
        System.out.print(c.printMeasure("WordReplacer"));
        System.out.print(c.printMeasure("RegexPatterns"));
        System.out.print(c.printMeasure("TrieRegexPattern"));
        assertTrue(c.getMeasure("ContentMappings") > c.getMeasure("TrieRegexPattern"));
        assertTrue(c.getMeasure("WordReplacer") > c.getMeasure("TrieRegexPattern"));
        assertTrue(c.getMeasure("RegexPatterns") > c.getMeasure("TrieRegexPattern"));
    }

    /**
     * Basic case replacements tests
     */
    @Test
    void caseReplacementsTest() {
        String[] originals = new String[] {"a", "b", "c"};
        String[] replaces = new String[] {"1", "2", "3"};
        String input = "a A b,B.c:C abc ABC ignored";

        // Test string replacements with and without ignoring the case
        assertEquals("1 A 2,B.3:C abc ABC ignored", WordReplacer.replaceWords(input, originals, replaces));
        assertEquals("1 1 2,2.3:3 abc ABC ignored", WordReplacer.replaceWordsIgnoreCase(input, originals, replaces));
        assertEquals(
                "1 1 2,2.3:3 abc ABC ignored",
                WordReplacer.replaceWords(input, triePattern(originals), replacementsMap(originals, replaces)));

        // Test string builder replacements
        StringBuilder inputSB = new StringBuilder(input);
        WordReplacer.replaceWords(inputSB, originals, replaces);
        assertEquals("1 A 2,B.3:C abc ABC ignored", inputSB.toString());

        inputSB = new StringBuilder(input);
        WordReplacer.replaceWordsIgnoreCase(inputSB, originals, replaces);
        assertEquals("1 1 2,2.3:3 abc ABC ignored", inputSB.toString());

        // Test string replacement (one) with and without ignoring the case
        assertEquals("1 A b,B.c:C abc ABC ignored", WordReplacer.replaceWord(input, originals[0], replaces[0]));
        assertEquals(
                "1 1 b,B.c:C abc ABC ignored", WordReplacer.replaceWordIgnoreCase(input, originals[0], replaces[0]));

        // Test string builder replacement (one)
        inputSB = new StringBuilder(input);
        WordReplacer.replaceWord(inputSB, originals[0], replaces[0]);
        assertEquals("1 A b,B.c:C abc ABC ignored", inputSB.toString());

        inputSB = new StringBuilder(input);
        WordReplacer.replaceWordIgnoreCase(inputSB, originals[0], replaces[0]);
        assertEquals("1 1 b,B.c:C abc ABC ignored", inputSB.toString());
    }

    @Test
    void replacementByShorterWordTest() {
        String input = "input one input";
        String[] words = new String[] {"input", "one"};
        String[] replaces = new String[] {"i", "o"};

        String result = "i o i";

        assertEquals(result, WordReplacer.replaceWords(input, words, replaces));
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

        assertEquals(result, WordReplacer.replaceWords(String.join(" ", words), words, replaces));
        assertEquals(
                result,
                WordReplacer.replaceWords(
                        String.join(" ", words), triePattern(words), replacementsMap(words, replaces)));
    }

    private static List<String> generateFakeListString(int lines) {
        assertTrue(lines < 1001);
        return Stream.generate(() -> FilteredOutputStreamTest.FAKE_TEXT)
                .limit(lines)
                .collect(Collectors.toList());
    }

    private static ContentMapping[] getContentMappings(String[] searches, String[] replaces) {
        ContentMapping[] contentMappings = new ContentMapping[searches.length];
        for (int i = 0; i < searches.length; i++) {
            contentMappings[i] = ContentMapping.of(searches[i], replaces[i]);
        }
        return contentMappings;
    }

    private static String[][][] getWordsLikeContentMapping(String[] searches, String[] replaces) {
        String[][][] result = new String[searches.length][2][4];

        for (int i = 0; i < searches.length; i++) {
            String original = searches[i];
            String replacement = replaces[i];

            String[] originals = new String[4];
            originals[0] = original;
            originals[1] = Functions.escape(searches[i]);
            originals[2] = original.replace("/", " » ");
            originals[3] = Functions.escape(originals[2]);

            String[] allReplaces = new String[] {replacement, replacement, replacement, replacement};

            result[i][0] = originals;
            result[i][1] = allReplaces;
        }

        return result;
    }

    private static Map<String, String> getReplacementsLikeContentMapping(String[][][] tokens) {
        Map<String, String> replacementMap = new HashMap<>();
        for (String[][] token : tokens) {
            for (int i = 0; i < token[0].length; i++) {
                replacementMap.put(token[0][i].toLowerCase(Locale.ENGLISH), token[1][i]);
            }
        }
        return replacementMap;
    }

    /**
     * Generate a trie regex pattern.
     * @param originals the original words
     * @param lowercase whether to force lowercase or not (lowercase used for case-insensitive matching in filters)
     * @return the Pattern
     */
    private static Pattern triePattern(String[] originals, boolean lowercase) {
        WordsTrie trie = new WordsTrie();
        for (String search : originals) {
            trie.add(lowercase ? search.toLowerCase(Locale.ENGLISH) : search);
        }
        return Pattern.compile(
                "(?<!\\w)" + trie.getRegex() + "(?!\\w)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Generate a replacement map <original , replacement> based on an array of originals and an array of replacements.
     * @param originals the original words
     * @param lowercase whether to force lowercase or not (lowercase used for case-insensitive matching in filters)
     * @return the Pattern
     */
    private static Map<String, String> replacementsMap(String[] originals, String[] replacements, boolean lowercase) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < originals.length; i++) {
            result.put(
                    lowercase ? originals[i].toLowerCase(Locale.ENGLISH) : originals[i],
                    replacements[i].replaceAll("\\\\", "\\\\\\\\").replaceAll("\\$", "\\\\\\$"));
        }
        return result;
    }

    private static Pattern triePattern(String[] searches) {
        return triePattern(searches, true);
    }

    private static Map<String, String> replacementsMap(String[] searches, String[] replaces) {
        return replacementsMap(searches, replaces, true);
    }

    /**
     * Create a union pattern (word1|word2|...) from a list of original words.
     * @param originals the original words
     * @return the pattern
     */
    private static Pattern unionPattern(String[] originals) {
        // Chunking is necessary here to prevent StackOverFlow in pattern matching unions
        final StringBuilder buf = new StringBuilder();
        if (originals.length < 1024) {
            buf.append("(?:");
            buf.append(Arrays.stream(originals).map(Pattern::quote).collect(Collectors.joining("|")));
            buf.append(")");
        } else {
            buf.append("(?:");
            int chunkSize = 1024;
            for (int i = 0; i < originals.length; i += chunkSize) {
                List<String> originalsChunk =
                        Arrays.asList(originals).subList(i, Math.min(i + chunkSize, originals.length));
                buf.append("(?:");
                buf.append(originalsChunk.stream().map(Pattern::quote).collect(Collectors.joining("|")));
                buf.append(")");
                buf.append("]|");
            }
            buf.deleteCharAt(buf.length() - 1);
            buf.append(')');
        }
        return Pattern.compile("(?<!\\w)" + buf + "(?!\\w)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
