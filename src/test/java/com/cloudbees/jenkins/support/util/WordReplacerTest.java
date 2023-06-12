package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.filter.ContentMapping;
import com.cloudbees.jenkins.support.filter.FilteredOutputStreamTest;
import com.cloudbees.jenkins.support.filter.WordsTrie;
import hudson.Functions;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WordReplacerTest {

    @Test
    public void wordReplacerTest() {
        //The ignore case doesn't work in these cases:
        //Chinese numbers lower: 一, 二,三,四,五,六,七,八,九,十,百,千,萬
        //Chinese numbers upper: 壹,貳,參,肆,伍,陸,柒,捌,玖,拾,佰,仟,萬

        String input =    "abcdefghijklmnñopqrstuvwxyz Beijing: 佩 One: 一, CasaBlanca: الدار البيضاء Casa Blanca. Numbers: 一, 二,三,四,五,六,七,八,九,十,百,千,萬";
        String expected = "abcdefghijklmnñopqrstuvwxyz *beijing*: *佩* One: 一, CasaBlanca: *الدار البيضاء* Casa *BLANCA*. Numbers: 一, 二,三,四,五,六,七,八,九,十,百,千,萬";
        // In an ideal world:
        // String expected = "abcdefghijklmnñopqrstuvwxyz *beijing*: *佩* One: *1*, CasaBlanca: *الدار البيضاء* Casa *BLANCA*. Numbers: *1*, 二,三,四,五,六,七,八,九,十,百,千,萬";

        String[] search = new String[] {"a", "Ñ", "beijing", "佩", "الدار البيضاء", "blaNca", "壹"};
        String[] replace = new String[] {"*a*", "*ñ*", "*beijing*", "*佩*", "*الدار البيضاء*", "*BLANCA*", "*1*"};


        String result = WordReplacer.replaceWordsIgnoreCase(input, search, replace);
        System.out.format("%s ->\n%s", input, result);
        assertEquals(expected, result);

        result = WordReplacer.replaceWords(input, triePattern(search), replacementsMap(search, replace));
        assertEquals(expected, result);
    }

    @Test
    public void sameResultTest() {
        String[] searches = new String[]{"one", "two"};
        String[] replaces = new String[]{"111", "111"};

        // Populate the content mapping objects to filter
        ContentMapping[] contentMappings = getContentMappings(searches, replaces);

        // Texts to test against
        // regexp doesn't replace ;word, we do. It's even better but we can't check it
        String[] inputs = new String[] {
                "", "a", "none", "one should be replaced", "onecar shouldn't be replaced", "two is replaced",
                "must replace two", "shouldn't replace twoo", "one!one,one!onetwoone_twoone#one?one:one->one\"one",
                "'one|twoone, \"one\\two one\ntwo one\ttwo one=two (one+two*one) all replaced ",
                "one\\two one\ntwo one\ttwo one=two (one+two*one) all replaced "};

        // For each text, filter using ContentMapping and with WordReplacer as well
        for (String input : inputs) {
            String resultCM = input;
            for (ContentMapping cm : contentMappings) {
                resultCM = cm.filter(resultCM);
            }

            String resultWordReplacer = WordReplacer.replaceWordsIgnoreCase(input, searches, replaces);

            // Both filtered strings should be the same
            assertEquals("The string replaced should be the same using RegExp or WordReplacer", resultCM, resultWordReplacer);

            resultWordReplacer = WordReplacer.replaceWords(input, triePattern(searches), replacementsMap(searches, replaces));

            // Both filtered strings should be the same
            assertEquals("The string replaced should be the same using RegExp or WordReplacer", resultCM, resultWordReplacer);
        }
    }

    @Ignore("It was useful to make the decision to move out of ContentMapping#filter and later out of WordReplacer " +
        "without Pattern. It has no sense to test. We keep it here for future cases.")
    @Test
    public void performanceTest() {
        // Create a lot of word and replaces (each character letter or digit. Aprox: 4070)
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            if (Character.isLetterOrDigit(c)) {
                words.add(String.valueOf(c));
                replaceList.add("**" + c + "**" );
            }
        }

        String[] searches = words.toArray(new String[0]);
        String[] replaces = replaceList.toArray(new String[0]);

        // Generate a fake long text (well, not so long to avoid lasts too much)
        List<String> text = generateFakeListString(10);
        //Result with 100 lines in i7
        //Point 'ContentMappings': 1.785 seconds since last point
        //Point 'WordReplacer': 6.114 seconds since last point
        //Point 'TrieRegexPattern': 649.0 milliseconds since last point

        //Create the content mappings
        ContentMapping[] contentMappings = getContentMappings(searches, replaces);

        // Create the searches and replaces for the WordReplacer mimicking the behavior of the Content Mapping
        String[][][] tokens = getWordsLikeContentMapping(searches, replaces);

        // Create the Trie pattern and replacements map mimicking the behavior of the Content Mapping
        Pattern triePattern = triePattern(searches);
        Map<String, String> replacementMap = getReplacementsLikeContentMapping(tokens);

        Chrono c = new Chrono("Test ContentMapping vs WordReplacer vs TrieRegexPattern");
        // Filter using WordReplacer in the same way as ContentMapping
        for (String line: text) {
            String resultCM = line;
            for (ContentMapping cm : contentMappings) {
                resultCM = cm.filter(resultCM);
            }
        }
        c.markFromPrevious("ContentMappings");

        // Filter using WordReplacer in the same way as ContentMapping
        for (String line: text) {
            String resultWR = line;
            for (String[][] token : tokens) {
                resultWR = WordReplacer.replaceWordsIgnoreCase(resultWR, token[0], token[1]);
            }
//            WordReplacer.replaceWordsIgnoreCase(line, searches, replaces);
        }
        c.markFromPrevious("WordReplacer");

        // Filter using TrieRegex pattern
        for (String line: text) {
            WordReplacer.replaceWords(line, triePattern, replacementMap);
        }
        c.markFromPrevious("TrieRegexPattern");

        System.out.print(c.printMeasure("ContentMappings"));
        System.out.print(c.printMeasure("WordReplacer"));
        System.out.print(c.printMeasure("TrieRegexPattern"));
        assertTrue(c.getMeasure("ContentMappings") > c.getMeasure("TrieRegexPattern"));
        assertTrue(c.getMeasure("WordReplacer") > c.getMeasure("TrieRegexPattern"));
    }

    /**
     * Basic case replacements tests
     */
    @Test
    public void caseReplacementsTest() {
        String[] originals = new String[]{"a", "b", "c"};
        String[] replaces  = new String[]{"1", "2", "3"};
        String input = "a A b,B.c:C abc ABC ignored";

        // Test string replacements with and without ignoring the case
        assertEquals("1 A 2,B.3:C abc ABC ignored", WordReplacer.replaceWords(input, originals, replaces));
        assertEquals("1 1 2,2.3:3 abc ABC ignored", WordReplacer.replaceWordsIgnoreCase(input, originals, replaces));
        assertEquals("1 1 2,2.3:3 abc ABC ignored", WordReplacer.replaceWords(input, triePattern(originals), replacementsMap(originals, replaces)));

        // Test string builder replacements
        StringBuilder inputSB = new StringBuilder(input);
        WordReplacer.replaceWords(inputSB, originals, replaces);
        assertEquals("1 A 2,B.3:C abc ABC ignored", inputSB.toString());

        inputSB = new StringBuilder(input);
        WordReplacer.replaceWordsIgnoreCase(inputSB, originals, replaces);
        assertEquals("1 1 2,2.3:3 abc ABC ignored", inputSB.toString());

        // Test string replacement (one) with and without ignoring the case
        assertEquals("1 A b,B.c:C abc ABC ignored", WordReplacer.replaceWord(input, originals[0], replaces[0]));
        assertEquals("1 1 b,B.c:C abc ABC ignored", WordReplacer.replaceWordIgnoreCase(input, originals[0], replaces[0]));

        // Test string builder replacement (one)
        inputSB = new StringBuilder(input);
        WordReplacer.replaceWord(inputSB, originals[0], replaces[0]);
        assertEquals("1 A b,B.c:C abc ABC ignored", inputSB.toString());

        inputSB = new StringBuilder(input);
        WordReplacer.replaceWordIgnoreCase(inputSB, originals[0], replaces[0]);
        assertEquals("1 1 b,B.c:C abc ABC ignored", inputSB.toString());
    }

    @Test
    public void replacementByShorterWordTest() {
        String input = "input one input";
        String[] words = new String[]{   "input",   "one"};
        String[] replaces = new String[]{"i", "o"};

        String result = "i o i";

        assertEquals(result, WordReplacer.replaceWords(input, words, replaces));
        assertEquals(result, WordReplacer.replaceWords(input, triePattern(words), replacementsMap(words, replaces)));
    }

    private List<String> generateFakeListString(int lines) {
        assertTrue(lines < 1001);
        return Stream.generate(() -> FilteredOutputStreamTest.FAKE_TEXT).limit(lines).collect(Collectors.toList());
    }

    private ContentMapping[] getContentMappings(String[] searches, String[] replaces) {
        ContentMapping[] contentMappings = new ContentMapping[searches.length];
        for (int i = 0; i < searches.length; i++) {
            contentMappings[i] = ContentMapping.of(searches[i], replaces[i]);
        }
        return contentMappings;
    }

    private String[][][] getWordsLikeContentMapping(String[] searches, String[] replaces) {
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

    private Map<String, String> getReplacementsLikeContentMapping(String[][][] tokens) {
        Map<String, String> replacementMap = new HashMap<>();
        for (String[][] token : tokens) {
            for(int i=0; i<token[0].length; i++) {
                replacementMap.put(token[0][i].toLowerCase(Locale.ENGLISH), token[1][i]);
            }
        }
        return replacementMap;
    }

    private Pattern triePattern(String [] searches) {
        WordsTrie trie = new WordsTrie();
        for (String search : searches) {
            trie.add(search);
        }
        return Pattern.compile("(?:\\b(?:" + trie.getRegex() + ")\\b)", Pattern.CASE_INSENSITIVE);
    }

    private Map<String, String> replacementsMap(String [] searches, String [] replaces) {
        Map<String, String> replacements = new HashMap<>();
        for (int i = 0; i < searches.length; i++) {
            replacements.put(searches[i].toLowerCase(Locale.ENGLISH), replaces[i]);
        }
        return replacements;
    }
}
