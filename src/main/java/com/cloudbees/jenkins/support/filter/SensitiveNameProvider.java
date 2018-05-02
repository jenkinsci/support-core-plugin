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

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Provides names that are considered sensitive and should be filtered when anonymizing data.
 *
 * @since TODO
 */
public interface SensitiveNameProvider extends ExtensionPoint {

    /**
     * @return all registered sensitive name providers
     */
    static ExtensionList<SensitiveNameProvider> all() {
        return ExtensionList.lookup(SensitiveNameProvider.class);
    }

    /**
     * @return a stream of sensitive names to anonymize
     */
    @Nonnull
    Stream<String> names();

    /**
     * @return the prefix to add to generated names which is concatenated with a '_'
     */
    @Nonnull
    String prefix();

    @Extension
    class Items implements SensitiveNameProvider {
        @Nonnull
        @Override
        public Stream<String> names() {
            return Jenkins.get()
                    .getItems().stream()
                    .flatMap(item -> Arrays.stream(item.getFullName().split("/")))
                    .distinct();
        }

        @Nonnull
        @Override
        public String prefix() {
            return "item";
        }
    }

    @Extension
    class Views implements SensitiveNameProvider {
        @Nonnull
        @Override
        public Stream<String> names() {
            return Jenkins.get()
                    .getViews().stream()
                    .map(View::getViewName);
        }

        @Nonnull
        @Override
        public String prefix() {
            return "view";
        }
    }

    @Extension
    class Nodes implements SensitiveNameProvider {
        @Nonnull
        @Override
        public Stream<String> names() {
            return Jenkins.get()
                    .getNodes().stream()
                    .map(Node::getNodeName);
        }

        @Nonnull
        @Override
        public String prefix() {
            return "node";
        }
    }

    @Extension
    class Computers implements SensitiveNameProvider {
        @Nonnull
        @Override
        public Stream<String> names() {
            return Arrays.stream(Jenkins.get().getComputers())
                    .map(Computer::getDisplayName);
        }

        @Nonnull
        @Override
        public String prefix() {
            return "computer";
        }
    }

    @Extension
    class Users implements SensitiveNameProvider {
        @Nonnull
        @Override
        public Stream<String> names() {
            return User.getAll().stream()
                    .map(User::getFullName);
        }

        @Nonnull
        @Override
        public String prefix() {
            return "user";
        }
    }

    // note that this takes lower priority than the other sources because when you have computers and labels with
    // the same name, the computer name should take preference as it is more specific
    @Extension(ordinal = -100)
    class Labels implements SensitiveNameProvider {
        @Nonnull
        @Override
        public Stream<String> names() {
            return Jenkins.get()
                    .getLabels().stream()
                    .map(Label::getDisplayName);
        }

        @Nonnull
        @Override
        public String prefix() {
            return "label";
        }
    }
}
