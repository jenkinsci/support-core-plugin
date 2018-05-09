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
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.User;
import hudson.model.View;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Restricted(NoExternalUse.class)
public class NameProvider implements ExtensionPoint {
    private final Supplier<Stream<String>> names;
    private final Supplier<String> fakes;

    private NameProvider(@Nonnull Supplier<Stream<String>> names, @Nonnull Supplier<String> fakes) {
        this.names = names;
        this.fakes = fakes;
    }

    public @Nonnull Stream<String> names() {
        return names.get();
    }

    public @Nonnull String generateFake() {
        return fakes.get();
    }

    public static @Nonnull ExtensionList<NameProvider> all() {
        return ExtensionList.lookup(NameProvider.class);
    }

    public static final @Extension NameProvider ITEMS = new NameProvider(
            () -> Jenkins.get()
                    .getItems().stream()
                    .flatMap(item -> Arrays.stream(item.getFullName().split("/")))
                    .distinct(),
            DataFaker.get().apply(faker -> "item_" + faker.app().name()));

    public static final @Extension NameProvider VIEWS = new NameProvider(
            () -> Jenkins.get()
                    .getViews().stream()
                    .map(View::getViewName),
            DataFaker.get().apply(faker -> "view_" + faker.space().moon()));

    public static final @Extension NameProvider NODES = new NameProvider(
            () -> Jenkins.get()
                    .getNodes().stream()
                    .map(Node::getNodeName),
            DataFaker.get().apply(faker -> "node_" + faker.internet().domainWord()));

    public static final @Extension NameProvider COMPUTERS = new NameProvider(
            () -> Arrays.stream(Jenkins.get().getComputers())
                    .map(Computer::getDisplayName),
            DataFaker.get().apply(faker -> "computer_" + faker.internet().domainWord()));

    public static final @Extension NameProvider USERS = new NameProvider(
            () -> User.getAll().stream()
                    .map(User::getFullName),
            DataFaker.get().apply(faker -> "user_" + faker.name().username()));

    // note that this takes lower priority than the other sources because when you have computers and labels with
    // the same name, the computer name should take preference as it is more specific
    public static final @Extension(ordinal = -100) NameProvider LABELS = new NameProvider(
            () -> Jenkins.get()
                    .getLabels().stream()
                    .map(Label::getDisplayName),
            DataFaker.get().apply(faker -> "label_" + faker.space().star()));
}
