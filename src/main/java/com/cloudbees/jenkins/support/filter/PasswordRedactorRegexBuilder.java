package com.cloudbees.jenkins.support.filter;

import com.cloudbees.jenkins.support.SupportPlugin;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PasswordRedactorRegexBuilder {

    private static final Logger LOGGER = Logger.getLogger(PasswordRedactorRegexBuilder.class.getName());

    public static final String ADDITIONAL_SECURITY_WORDS_FILENAME = "security-stop-words.txt";
    private static final Set<String> SECRET_WORDS = getSecretWords();
    // ex:
    // (?i)(private[^=\s]*|key[^=\s]*|passwd[^=\s]*|password[^=\s]*|token[^=\s]*|passphrase[^=\s]*|secret[^=\s]*)\s*=\s*([^,\s\0]*)
    public static final Pattern PASSWORD_PATTERN = getPasswordPattern(SECRET_WORDS);
    // ex: "(?i).*(password|private|passwd|passphrase|key|token).*"
    public static final String SECRET_PROPERTY_MATCHER = getSecretMatcher(SECRET_WORDS);

    private PasswordRedactorRegexBuilder() {
        // to hide the implicit public constructor
    }

    // package private for tests
    static Pattern getPasswordPattern(Set<String> secretWords) {
        if (secretWords.isEmpty()) {
            return null;
        }
        return Pattern.compile(buildRegex(secretWords, "[^=\\s]*", "(?i)(", ")\\s*=\\s*([^,\\s\0<'`]*)"));
    }

    // package private for tests
    static String getSecretMatcher(Set<String> secretWords) {
        if (secretWords.isEmpty()) {
            return null;
        }
        return buildRegex(secretWords, "", "(?i).*(", ").*");
    }

    private static String buildRegex(Set<String> words, String wordSuffix, String prefix, String suffix) {
        return words.stream()
                .map(securityWord -> securityWord + wordSuffix)
                .collect(Collectors.joining("|", prefix, suffix));
    }

    private static Set<String> getDefaultSecurityWords() {
        return new HashSet<>(Arrays.asList(
                "password",
                "token",
                "passwd",
                "passphrase",
                "private",
                "key",
                "secret",
                "AWS_ACCESS_KEY_ID",
                "AWS_SECRET_ACCESS_KEY"));
    }

    private static Set<String> getSecretWords() {
        Set<String> words = new HashSet<>();
        String fileLocation = SupportPlugin.getRootDirectory() + "/" + ADDITIONAL_SECURITY_WORDS_FILENAME;
        LOGGER.log(Level.FINE, () -> "Attempting to load user provided secret words from '" + fileLocation + "'.");
        File f = new File(fileLocation);
        if (!f.canRead()) {
            LOGGER.log(
                    Level.FINE,
                    () -> "Could not load user provided secret words as '" + fileLocation + "' is not readable.");
            if (!f.exists()) {
                try {
                    Files.createDirectories(f.getParentFile().toPath());
                    Files.write(f.toPath(), getDefaultSecurityWords());
                } catch (IOException ex) {
                    LOGGER.log(Level.FINE, () -> "Could not initialize '" + fileLocation + "' with default values.");
                }
            }
            words.addAll(getDefaultSecurityWords());
            return words;
        } else {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(fileLocation), Charset.defaultCharset()))) {
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    if (!line.isBlank()) {
                        words.add(line);
                    }
                }
                return words;
            } catch (IOException ex) {
                LOGGER.log(
                        Level.WARNING,
                        ex,
                        () -> "Could not load user provided security words. there was an error reading "
                                + fileLocation);
            }
        }
        return words;
    }
}
