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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.XmlFile;
import hudson.model.AbstractItem;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.kohsuke.randname.RandomNameGenerator;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filters contents based on names provided by all {@linkplain SensitiveNameProvider known sources}.
 *
 * @author Matt Sicker
 * @see SensitiveNameProvider
 * @since 2.48
 */
@Extension
public class DefaultContentFilter extends ManagementLink implements ContentFilter, Saveable {

    public static DefaultContentFilter get() {
        return all().get(DefaultContentFilter.class);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultContentFilter.class);
    private static final String ALT_SEPARATOR = " » ";

    private volatile boolean enabled;
    private final Map<String, Replacer> mappings = new ConcurrentHashMap<>();
    private final RandomNameGenerator randomNameGenerator = new RandomNameGenerator();
    private final Set<String> stopWords = ConcurrentHashMap.newKeySet();

    public DefaultContentFilter() {
        stopWords.addAll(Arrays.asList(
                "jenkins", "node", "master", "computer",
                "item", "label", "view", "all", "unknown",
                "user", "anonymous", "authenticated"
        ));
        load();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public Map<String, String> getMappings() {
        Map<String, String> map = new HashMap<>(mappings.size());
        for (Map.Entry<String, Replacer> entry : mappings.entrySet()) {
            map.put(entry.getKey(), entry.getValue().replacement);
        }
        return map;
    }

    public Set<String> getStopWords() {
        return stopWords;
    }

    @CheckForNull
    @Override
    public Permission getRequiredPermission() {
        return SupportPlugin.CREATE_BUNDLE;
    }

    @RequirePOST
    public void doUpdate(StaplerRequest req, StaplerResponse res) throws ServletException, IOException {
        req.bindJSON(this, req.getSubmittedForm());
        res.forwardToPreviousPage(req);
    }

    @Nonnull
    @Override
    public String filter(@Nonnull String input) {
        if (input.isEmpty() || !enabled) return input;
        String filtered = input;
        for (Replacer replacer : mappings.values()) {
            filtered = replacer.replaceAll(filtered);
        }
        return filtered;
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), "secrets/" + getClass().getCanonicalName() + ".xml"));
    }

    @Override
    public synchronized void reload() {
        ExtensionList.lookup(AbstractItem.class).forEach(item -> {
            stopWords.add(item.getTaskNoun());
            stopWords.add(item.getPronoun());
        });
        ExtensionList.lookup(SensitiveNameProvider.class).stream()
                .flatMap(provider -> provider.names().map(name -> ImmutablePair.of(name, provider.prefix())))
                .filter(pair -> !stopWords.contains(pair.left.toLowerCase(Locale.ENGLISH)))
                .forEach(pair -> mappings.computeIfAbsent(pair.left, ignored -> new Replacer(pair.left, pair.right + '_' + randomNameGenerator.next())));
    }

    @Override
    public synchronized void save() {
        reload();
        XmlFile file = getConfigFile();
        SerializationProxy proxy = new SerializationProxy();
        proxy.setEnabled(enabled);
        Map<String, String> mappings = new HashMap<>(this.mappings.size());
        for (Map.Entry<String, Replacer> entry : this.mappings.entrySet()) {
            mappings.put(entry.getKey(), entry.getValue().replacement);
        }
        proxy.setMappings(mappings);
        proxy.setStopWords(new HashSet<>(stopWords));
        try {
            file.write(proxy);
        } catch (IOException e) {
            LOGGER.warn("Could not save file {}", file, e);
        }
    }

    private synchronized void load() {
        XmlFile file = getConfigFile();
        if (!file.exists()) return;
        SerializationProxy proxy = new SerializationProxy();
        try {
            file.unmarshal(proxy);
            enabled = proxy.isEnabled();
            stopWords.clear();
            stopWords.addAll(proxy.getStopWords());
            mappings.clear();
            for (Map.Entry<String, String> entry : proxy.getMappings().entrySet()) {
                mappings.put(entry.getKey(), new Replacer(entry.getKey(), entry.getValue()));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not load file {}", file, e);
        }
        reload();
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "secure.png";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "anonymized";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "Anonymized Items";
    }

    @Override
    public String getDescription() {
        return "Configure mappings between real items and their anonymized counterparts.";
    }

    private static class Replacer {
        private final Collection<Pattern> patterns;
        private final String replacement;

        private Replacer(String original, String replacement) {
            this.patterns = generate(original);
            this.replacement = replacement;
        }

        private String replaceAll(CharSequence input) {
            String filtered = input.toString();
            for (Pattern pattern : patterns) {
                filtered = pattern.matcher(filtered).replaceAll(replacement);
            }
            return filtered;
        }

        private static Collection<Pattern> generate(String original) {
            String alternative = original.replace("/", ALT_SEPARATOR);
            return Stream.of(original, Functions.escape(original), alternative, Functions.escape(alternative))
                    .map(s -> Pattern.compile("\\b(" + Pattern.quote(s) + ")\\b", Pattern.CASE_INSENSITIVE))
                    .collect(Collectors.toList());
        }
    }

    private static class SerializationProxy {
        private boolean enabled;
        private Map<String, String> mappings;
        private Set<String> stopWords;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getMappings() {
            return mappings;
        }

        public void setMappings(Map<String, String> mappings) {
            this.mappings = mappings;
        }

        public Set<String> getStopWords() {
            return stopWords;
        }

        public void setStopWords(Set<String> stopWords) {
            this.stopWords = stopWords;
        }
    }
}
