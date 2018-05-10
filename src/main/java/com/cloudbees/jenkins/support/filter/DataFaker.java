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

import com.github.javafaker.Faker;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides a way to bind Faker data generators to a Faker instance.
 *
 * @since TODO
 */
@Extension
@Restricted(NoExternalUse.class)
public class DataFaker implements ExtensionPoint, Function<Function<Faker, String>, Supplier<String>> {

    /**
     * @return the singleton instance
     */
    public static DataFaker get() {
        return ExtensionList.lookupSingleton(DataFaker.class);
    }

    private final Faker faker = new Faker(Locale.ENGLISH);

    /**
     * Binds the provided Faker generator to a configured Faker instance and normalizes the name.
     */
    @Override
    public Supplier<String> apply(@Nonnull Function<Faker, String> generator) {
        return () -> generator.apply(faker).toLowerCase(Locale.ENGLISH).replace(' ', '_');
    }

}
