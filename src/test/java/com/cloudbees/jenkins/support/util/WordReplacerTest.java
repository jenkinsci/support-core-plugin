package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.filter.ContentMapping;
import com.cloudbees.jenkins.support.filter.FilteredOutputStreamTest;
import hudson.Functions;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Assert.assertEquals(expected, result);
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
            Assert.assertEquals("The string replaced should be the same using RegExp or WordReplacer", resultCM, resultWordReplacer);
        }
    }

    @Ignore("It was useful to make the decision to move out of Reg Exp. As the implementation of the Content Mapping is" +
            "now WordReplacer, it has no sense to test. We keep it here for future cases.")
    @Test
    public void performanceTest() {
        // Create a lot of word and replaces (each character letter or digit. Aprox: 4070)
        List<String> words = new ArrayList<>();
        List<String> replaceList = new ArrayList<>();
        for(char c=0; c < Character.MAX_VALUE; c++) {
            if(Character.isLetterOrDigit(c)) {
                words.add(String.valueOf(c));
                replaceList.add("**" + c + "**" );
            }
        }

        String[] searches = words.toArray(new String[0]);
        String[] replaces = replaceList.toArray(new String[0]);

        // Generate a fake long text (well, not so long to avoid lasts too much)
        List<String> text = generateFakeListString(10);
        //Result with 100 lines in i7
        //Point 'RegExp': 31.16 seconds since last point
        //Point 'WordReplacer': 731.0 milliseconds since last point

        //Create the content mappings
        ContentMapping[] contentMappings = getContentMappings(searches, replaces);

        // Create the searches and replaces for the WordReplacer mimicking the behavior of the Content Mapping
        String[][][] tokens = getWordsLikeContentMapping(searches, replaces);

        Chrono c = new Chrono("Test ContentMapping Vs WordReplacer");
        // Filter using ContentMappings
        for (String line : text) {
            String resultCM = line;
            for (ContentMapping cm : contentMappings) {
                resultCM = cm.filter(resultCM);
            }
        }
        c.markFromPrevious("ContentMapping#filter");

        // Filter using WordReplacer in the same way as ContentMapping
        for (String line: text) {
            String resultWR = line;
            for (String[][] token : tokens) {
                resultWR = WordReplacer.replaceWordsIgnoreCase(resultWR, token[0], token[1]);
            }
        }
        c.markFromPrevious("WordReplacer");

        System.out.println(c.printMeasure("ContentMapping#filter"));
        System.out.println(c.printMeasure("WordReplacer"));
        Assert.assertTrue(c.getMeasure("ContentMapping#filter") > c.getMeasure("WordReplacer"));

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
        Assert.assertEquals("1 A 2,B.3:C abc ABC ignored", WordReplacer.replaceWords(input, originals, replaces));
        Assert.assertEquals("1 1 2,2.3:3 abc ABC ignored", WordReplacer.replaceWordsIgnoreCase(input, originals, replaces));

        // Test string builder replacements
        StringBuilder inputSB = new StringBuilder(input);
        WordReplacer.replaceWords(inputSB, originals, replaces);
        Assert.assertEquals("1 A 2,B.3:C abc ABC ignored", inputSB.toString());

        inputSB = new StringBuilder(input);
        WordReplacer.replaceWordsIgnoreCase(inputSB, originals, replaces);
        Assert.assertEquals("1 1 2,2.3:3 abc ABC ignored", inputSB.toString());

        // Test string replacement (one) with and without ignoring the case
        Assert.assertEquals("1 A b,B.c:C abc ABC ignored", WordReplacer.replaceWord(input, originals[0], replaces[0]));
        Assert.assertEquals("1 1 b,B.c:C abc ABC ignored", WordReplacer.replaceWordIgnoreCase(input, originals[0], replaces[0]));

        // Test string builder replacement (one)
        inputSB = new StringBuilder(input);
        WordReplacer.replaceWord(inputSB, originals[0], replaces[0]);
        Assert.assertEquals("1 A b,B.c:C abc ABC ignored", inputSB.toString());

        inputSB = new StringBuilder(input);
        WordReplacer.replaceWordIgnoreCase(inputSB, originals[0], replaces[0]);
        Assert.assertEquals("1 1 b,B.c:C abc ABC ignored", inputSB.toString());
    }

    @Test
    public void indexOutOfBoundsExceptionTest() {
        String input = "input one input";
        String[] words = new String[]{   "input",   "one"};
        String[] replaces = new String[]{"", ""};

        String result = "  ";

        String replaced = WordReplacer.replaceWords(input, words, replaces);

        Assert.assertEquals(result, replaced);
    }


    private List<String> generateFakeListString(int lines) {
        Assert.assertTrue(lines < 1001);
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
}
