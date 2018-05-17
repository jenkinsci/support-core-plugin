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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Logger LOGGER = Logger.getLogger(ContentMappings.class.getName());

    private final Set<String> stopWords;
    private final Map<String, ContentMapping> mappings;

    private ContentMappings(@Nonnull XmlProxy proxy) {
        stopWords = proxy.stopWords == null ? getDefaultStopWords() : proxy.stopWords;
        mappings = proxy.stopWords == null
                ? new ConcurrentHashMap<>()
                : proxy.mappings.stream().collect(toConcurrentMap(ContentMapping::getOriginal, Function.identity(), (a, b) -> {throw new IllegalArgumentException();}, ConcurrentSkipListMap::new));
    }

    private static Set<String> getDefaultStopWords() {
        return new HashSet<>(Arrays.asList(
                "jenkins", "node", "master", "computer",
                "item", "label", "view", "all", "unknown",
                "user", "anonymous", "authenticated",
                "everyone", "system"
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
        return mappings.computeIfAbsent(original, generator.andThen(mapping -> {
            try {
                save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not save mappings file", e);
            }
            return mapping;
        }));
    }

    public void reload() {
        Jenkins.get().allItems(AbstractItem.class).forEach(item -> {
            stopWords.add(item.getTaskNoun().toLowerCase(Locale.ENGLISH));
            stopWords.add(item.getPronoun().toLowerCase(Locale.ENGLISH));
        });
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
}
