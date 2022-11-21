/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.filter;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.util.Persistence;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toMap;

/**
 * Holds all anonymized content mappings and provides a management view to see those mappings.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class ContentMappings extends ManagementLink implements Saveable, Iterable<ContentMapping> {

    /**
     * Name of the file containing additional user-provided stop words.
     */
    private static final String ADDITIONAL_STOP_WORDS_FILENAME = "additional-stop-words.txt";

    /**
     * Property to set to add <b>additional</b> stop words.
     * The location should point to a line separated file containing words. Each line is treated as a word. 
     */
    static final String ADDITIONAL_STOP_WORDS_PROPERTY = ContentMappings.class.getName()+".additionalStopWordsFile";
    
    /**
     * @return the singleton instance
     */
    public static ContentMappings get() {
        return all().get(ContentMappings.class);
    }

    /**
     * Constructs a new ContentMappings using an existing config file or default settings if not found.
     */
    public static @Extension ContentMappings newInstance() throws IOException {
        ContentMappings mappings = Persistence.load(ContentMappings.class);
        if (mappings == null) {
            mappings = (ContentMappings) new XmlProxy().readResolve();
        }
        return mappings;
    }

    private static final Comparator<String> BY_LENGTH = Comparator.comparingLong(String::length);
    private static final Comparator<String> BY_NAME = Comparator.comparing(Function.identity());
    private static final Comparator<String> COMPARATOR = BY_LENGTH.reversed().thenComparing(BY_NAME);
    private static final Logger LOGGER = Logger.getLogger(ContentMappings.class.getName());

    private final Set<String> stopWords;
    private final Map<String, ContentMapping> mappings;

    private ContentMappings(@NonNull XmlProxy proxy) {
        if (proxy.stopWords == null) {
            stopWords = getDefaultStopWords();
        } else {
            stopWords = proxy.stopWords;
            stopWords.add(Jenkins.VERSION);
        }

        // JENKINS-54688
        stopWords.addAll(getAllowedOSName());

        // Add single character words
        stopWords.addAll(getAllAsciiCharacters());

        // Add additional stop words
        stopWords.addAll(getAdditionalStopWords());

        mappings = proxy.mappings == null
                ? new ConcurrentSkipListMap<>(COMPARATOR)
                : proxy.mappings.stream()
                    .filter(mapping -> !stopWords.contains(mapping.getOriginal().toLowerCase(Locale.ENGLISH)))
                    .collect(toConcurrentMap(ContentMapping::getOriginal, Function.identity(), (a, b) -> {throw new IllegalArgumentException();}, () -> new ConcurrentSkipListMap<>(COMPARATOR)));
    }

    /**
     * Get the stop words by default
     * @return the stop words to avoid being replaced.
     */
    private static Set<String> getDefaultStopWords() {
        return new HashSet<>(Arrays.asList(
                "jenkins", "node", "master", "computer",
                "item", "label", "view", "all", "unknown",
                "user", "anonymous", "authenticated",
                "everyone", "system", "admin", Jenkins.VERSION
        ));
    }

    /**
     * To avoid corrupting the content of the files in the bundle just in case we have an object name as 'a' or '.', we
     * avoid replacing one single character (ascii codes actually). A one single character in other languages could
     * have a meaning, so we remain replacing them. Example: 日 (Sun)
     * @return Set of characters in ascii code chart
     */
    private static Set<String> getAllAsciiCharacters() {
        final int SPACE = ' '; //20
        final int TILDE = '~'; //126
        Set<String> singleChars = new HashSet<>(TILDE - SPACE + 1);

        for (char i = SPACE; i <= TILDE; i++) {
            singleChars.add(Character.toString(i));
        }

        return singleChars;
    }

    private static Set<String> getAdditionalStopWords() {
        Set<String> words = new HashSet<>();
        String fileLocationFromProperty = System.getProperty(ADDITIONAL_STOP_WORDS_PROPERTY);
        String fileLocation = fileLocationFromProperty == null
            ? SupportPlugin.getRootDirectory() + "/" + ADDITIONAL_STOP_WORDS_FILENAME
            : fileLocationFromProperty;
        LOGGER.log(Level.FINE, "Attempting to load user provided stop words from ''{0}''.", fileLocation);
        File f = new File(fileLocation);
        if (f.exists()) {
            if (!f.canRead()) {
                LOGGER.log(Level.WARNING, "Could not load user provided stop words as " + fileLocation + " is not readable.");
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileLocation), Charset.defaultCharset()))) {
                    for (String line = br.readLine(); line != null; line = br.readLine()) {
                        if (StringUtils.isNotEmpty(line)) {
                            words.add(line);
                        }
                    }
                    return words;
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Could not load user provided stop words. there was an error reading " + fileLocation, ex);
                }
            }
        } else if (fileLocationFromProperty != null) {
            LOGGER.log(Level.WARNING, "Could not load user provided stop words as " + fileLocationFromProperty + " does not exists.");
        }
        return words;
    }

    private static Set<String> getAllowedOSName() {
        return new HashSet<>(Arrays.asList(
                "linux", "windows", "win", "mac", "macos", "macosx",
                "mac os x", "ubuntu", "debian", "fedora", "red hat",
                "sunos", "freebsd"
        ));
    }

    /**
     * @return the set of stop words to ignore when filtering
     */
    public @NonNull Set<String> getStopWords() {
        return Collections.unmodifiableSet(stopWords);
    }

    /**
     * @return the map of original to replacement values known to this instance
     */
    public @NonNull Map<String, String> getMappings() {
        return mappings.values().stream().collect(toMap(ContentMapping::getOriginal, ContentMapping::getReplacement));
    }

    /**
     * Looks up or creates a new ContentMapping for the given original string and a ContentMapping generator.
     */
    public @NonNull ContentMapping getMappingOrCreate(@NonNull String original, @NonNull Function<String, ContentMapping> generator) {
        boolean isNew = !mappings.containsKey(original);
        ContentMapping mapping = mappings.computeIfAbsent(original, generator);
        try {
            if (isNew) {
                save();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save mappings file", e);
        }
        return mapping;
    }

    public void reload() {
        Jenkins.get().allItems(AbstractItem.class).forEach(item -> {
            stopWords.add(item.getTaskNoun().toLowerCase(Locale.ENGLISH));
            stopWords.add(item.getPronoun().toLowerCase(Locale.ENGLISH));
        });
    }

    protected void clear() {
        stopWords.clear();
        stopWords.addAll(getDefaultStopWords());
        mappings.clear();
    }

    @Override
    public void save() throws IOException {
        Persistence.save(this);
    }

    @Override
    public Iterator<ContentMapping> iterator() {
        return mappings.values().iterator();
    }

    @Override
    public void forEach(Consumer<? super ContentMapping> action) {
        mappings.values().forEach(action);
    }

    @Override
    public Spliterator<ContentMapping> spliterator() {
        return mappings.values().spliterator();
    }

    private Object writeReplace() {
        XmlProxy proxy = new XmlProxy();
        proxy.stopWords = new HashSet<>(stopWords);
        proxy.stopWords.remove(Jenkins.VERSION);
        proxy.mappings = new HashSet<>(mappings.values());
        return proxy;
    }

    private static class XmlProxy {
        private Set<String> stopWords;
        private Set<ContentMapping> mappings;

        private Object readResolve() {
            return new ContentMappings(this);
        }
    }

    @Override
    public @NonNull String getIconFileName() {
        return "/plugin/support-core/images/support.svg";
    }

    @Override
    public @NonNull String getDisplayName() {
        return Messages.ContentMappings_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.ContentMappings_Description();
    }

    @Override
    public @NonNull String getUrlName() {
        return "anonymizedMappings";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.SECURITY;
    }
}
