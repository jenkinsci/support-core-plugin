package com.cloudbees.jenkins.support.util;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class WordReplacer {

    /**
     * Replace all matches in the normalized text by splicing replacements into the original input at matching offsets.
     * The pattern is matched against {@code normalized}, but replacements are spliced into {@code input} using the
     * same offsets. This is safe when normalization preserves offset alignment (never changes {@code charCount(cp)}).
     * <p>
     * Unlike {@link #replaceWords(String, Pattern, Function)}, this method does not use {@code Matcher#appendReplacement},
     * so {@code \} and {@code $} in replacements do not need escaping.
     *
     * @param input the original text, returned unchanged if no matches
     * @param normalized the case-normalized copy of {@code input}, matched against the pattern
     * @param pattern the pattern to match (should have no case-insensitive flags since {@code normalized} is pre-normalized)
     * @param replace function accepting a matched substring from {@code normalized} and returning its replacement
     * @return {@code input} itself when no matches; otherwise a new string with replacements spliced in
     */
    public static String replaceWordsByOffset(
            String input, String normalized, Pattern pattern, Function<String, String> replace) {
        java.util.regex.Matcher m = pattern.matcher(normalized);
        if (!m.find()) {
            return input; // fast path: no allocation when nothing matches
        }
        StringBuilder out = new StringBuilder(input.length());
        int last = 0;
        do {
            out.append(input, last, m.start());
            out.append(replace.apply(normalized.substring(m.start(), m.end())));
            last = m.end();
        } while (m.find());
        out.append(input, last, input.length());
        return out.toString();
    }

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
