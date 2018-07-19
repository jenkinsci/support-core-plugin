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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Strategy for providing a stream of names to anonymize along with an accompanying name generator.
 *
 * @see SensitiveContentFilter
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class NameProvider implements ExtensionPoint {
    private final Supplier<Stream<String>> names;
    private final Supplier<String> fakes;

    private NameProvider(@Nonnull Supplier<Stream<String>> names, @Nonnull Supplier<String> fakes) {
        this.names = names;
        this.fakes = fakes;
    }

    /**
     * @return a stream of names to anonymize
     */
    public @Nonnull Stream<String> names() {
        return names.get();
    }

    /**
     * @return a new fake name to use for anonymization
     */
    public @Nonnull String generateFake() {
        return fakes.get();
    }

    /**
     * @return all registered NameProviders
     */
    public static @Nonnull ExtensionList<NameProvider> all() {
        return ExtensionList.lookup(NameProvider.class);
    }

    private static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static <T> Stream<T> stream(T[] array) {
        return StreamSupport.stream(Arrays.spliterator(array), false);
    }

    private static <T> Stream<T> stream(Collection<T> collection) {
        return collection.stream();
    }

    /**
     * Provides the names of items.
     */
    public static final @Extension NameProvider ITEMS = new NameProvider(
            () -> stream(Jenkins.get().allItems())
                    .flatMap(item -> Stream.of(item.getName(), item.getDisplayName())),
            DataFaker.get().apply(name -> "item_" + name));

    /**
     * Provides the names of view.
     */
    public static final @Extension NameProvider VIEWS = new NameProvider(
            () -> stream(Jenkins.get().getViews())
                    .flatMap(view -> Stream.of(view.getViewName(), view.getDisplayName())),
            DataFaker.get().apply(name -> "view_" + name));

    /**
     * Provides the names of nodes.
     */
    public static final @Extension NameProvider NODES = new NameProvider(
            () -> stream(Jenkins.get().getNodes())
                    .flatMap(node -> Stream.of(node.getNodeName(), node.getDisplayName())),
            DataFaker.get().apply(name -> "node_" + name));

    /**
     * Provides the names of computers.
     */
    public static final @Extension NameProvider COMPUTERS = new NameProvider(
            () -> stream(Jenkins.get().getComputers())
                    .flatMap(computer -> Stream.of(computer.getName(), computer.getDisplayName())),
            DataFaker.get().apply(name -> "computer_" + name));

    /**
     * Provides the names of users.
     */
    public static final @Extension NameProvider USERS = new NameProvider(
            () -> stream(User.getAll())
                    .flatMap(user -> Stream.of(user.getId(), user.getFullName(), user.getDisplayName())),
            DataFaker.get().apply(name -> "user_" + name));

    /**
     * Provides the names of labels. Note that this extension is given a lower priority than the others to avoid
     * naming conflicts between labels and nodes/computers.
     */
    public static final @Extension(ordinal = -100) NameProvider LABELS = new NameProvider(
            () -> stream(Jenkins.get().getLabels())
                    .map(Label::getDisplayName),
            DataFaker.get().apply(name -> "label_" + name));
}
