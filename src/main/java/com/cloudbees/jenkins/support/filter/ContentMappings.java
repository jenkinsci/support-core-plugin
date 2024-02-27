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

import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toMap;

import com.cloudbees.jenkins.support.util.Persistence;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractItem;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import java.io.IOException;
import java.util.Collection;
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
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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
        return ExtensionList.lookupSingleton(ContentMappings.class);
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
        stopWords = proxy.stopWords != null ? proxy.stopWords : new HashSet<>();
        stopWords.addAll(StopWords.all().stream()
                .map(StopWords::getWords)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()));

        mappings = proxy.mappings == null
                ? new ConcurrentSkipListMap<>(COMPARATOR)
                : proxy.mappings.stream()
                        .filter(mapping ->
                                !stopWords.contains(mapping.getOriginal().toLowerCase(Locale.ENGLISH)))
                        .collect(toConcurrentMap(
                                ContentMapping::getOriginal,
                                Function.identity(),
                                (a, b) -> {
                                    throw new IllegalArgumentException();
                                },
                                () -> new ConcurrentSkipListMap<>(COMPARATOR)));
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
    public @NonNull ContentMapping getMappingOrCreate(
            @NonNull String original, @NonNull Function<String, ContentMapping> generator) {
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
        stopWords.addAll(ExtensionList.lookupSingleton(DefaultStopWords.class).getWords());
        mappings.clear();
    }

    @Override
    public void save() throws IOException {
        Persistence.save(this);
    }

    @Override
    @NonNull
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
