package com.cloudbees.jenkins.support.util;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class WordReplacer {

    /**
     * Replace all matches in the input by their replacement. Matcher#appendReplacement is used and therefore `\` and
     * `$` characters must be escaped in the replacement string.
     * NOTE: To ignore casing, Pattern must be case-insensitive or contain all possible cases. And replacements must
     * either be a case-insensitive map or have lowercase keys.
     * @param input the text where the replacements take place
     * @param pattern the pattern
     * @param replacements the new words to use
     */
    public static String replaceWords(String input, Pattern pattern, Map<String, String> replacements) {
        return replaceWords(input, pattern, s -> replacements.get(s.toLowerCase(Locale.ENGLISH)));
    }

    /**
     * Replace all matches in the input by their replacement. Matcher#appendReplacement is used and therefore `\` and
     * `$` characters must be escaped in the replacement string.
     * NOTE: To ignore casing, Pattern must be case-insensitive or contain all possible cases.
     * @param input the text where the replacements take place
     * @param pattern the pattern
     * @param replace the replace function. Accepts the matched word, and return the replacement
     */
    public static String replaceWords(String input, Pattern pattern, Function<String, String> replace) {
        return pattern.matcher(input).replaceAll(matchResult -> replace.apply(matchResult.group()));
    }
}
