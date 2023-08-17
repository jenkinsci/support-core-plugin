package com.cloudbees.jenkins.support.filter;

import hudson.Extension;
import hudson.ExtensionList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jenkinsci.remoting.SerializableOnlyOverRemoting;

@Extension
public class PasswordRedactor implements SerializableOnlyOverRemoting {

    private static final Logger LOGGER = Logger.getLogger(PasswordRedactor.class.getName());
    public static final String REDACTED = "REDACTED";
    public static final List<String> FILES_WITH_SECRETS =
            Collections.unmodifiableList(Arrays.asList("cmdline", "environ"));

    private final Pattern pattern;
    private final String matcher;

    public static PasswordRedactor get() {
        return ExtensionList.lookupSingleton(PasswordRedactor.class);
    }

    public PasswordRedactor() {
        this.pattern = PasswordRedactorRegexBuilder.PASSWORD_PATTERN;
        this.matcher = PasswordRedactorRegexBuilder.SECRET_PROPERTY_MATCHER;
    }

    // for tests usage
    PasswordRedactor(Pattern pattern, String matcher) {
        this.pattern = pattern;
        this.matcher = matcher;
    }

    public String redact(String input) {
        if (pattern == null) {
            // 'security-stop-words.txt' is empty
            return input;
        }
        Matcher patternMatcher = pattern.matcher(input);
        while (patternMatcher.find()) {
            LOGGER.log(Level.FINE, "Argument ''{0}'' contain secret data", patternMatcher.group(1));
            String secretValue = patternMatcher.group(2);
            input = input.replaceFirst("=\\s*" + Pattern.quote(secretValue), "=" + REDACTED);
        }
        return input;
    }

    public Map<String, String> redact(Map<String, String> properties) {
        if (matcher == null) {
            // 'security-stop-words.txt' is empty
            return properties;
        }
        Map<String, String> redacted = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().matches(matcher)) {
                LOGGER.log(Level.FINE, "Argument ''{0}'' contain secret data", entry.getKey());
                redacted.put(entry.getKey(), REDACTED);
            } else {
                redacted.put(entry.getKey(), redact(entry.getValue()));
            }
        }
        return redacted;
    }

    public boolean match(String value) {
        if (matcher == null) {
            // 'security-stop-words.txt' is empty
            return false;
        }
        return value.matches(matcher);
    }
}
