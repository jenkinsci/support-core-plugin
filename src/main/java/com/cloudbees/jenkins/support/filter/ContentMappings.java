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

import com.cloudbees.jenkins.support.util.Persistence;
import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.IOException;
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

    private ContentMappings(@Nonnull XmlProxy proxy) {
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
    public @Nonnull Set<String> getStopWords() {
        return Collections.unmodifiableSet(stopWords);
    }

    /**
     * @return the map of original to replacement values known to this instance
     */
    public @Nonnull Map<String, String> getMappings() {
        return mappings.values().stream().collect(toMap(ContentMapping::getOriginal, ContentMapping::getReplacement));
    }

    /**
     * Looks up or creates a new ContentMapping for the given original string and a ContentMapping generator.
     */
    public @Nonnull ContentMapping getMappingOrCreate(@Nonnull String original, @Nonnull Function<String, ContentMapping> generator) {
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
    public @Nonnull String getIconFileName() {
        return "/plugin/support-core/images/48x48/support.png";
    }

    @Override
    public @Nonnull String getDisplayName() {
        return Messages.ContentMappings_DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.ContentMappings_Description();
    }

    @Override
    public @Nonnull String getUrlName() {
        return "anonymizedMappings";
    }
    
    /**
     * Name of the category for this management link.
     * TBD: Use getCategory when core requirement is greater or equal to 2.226 
     */
    public @Nonnull String getCategoryName() {
        return "SECURITY";
    }    
}
