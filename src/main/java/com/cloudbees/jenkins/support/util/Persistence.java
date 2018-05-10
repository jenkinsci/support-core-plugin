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

package com.cloudbees.jenkins.support.util;

import hudson.BulkChange;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * Utility methods for persisting non-Describable classes.
 *
 * @see Saveable
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public final class Persistence {

    /**
     * @return the default config file for the given class
     */
    public static @Nonnull <T extends Saveable> XmlFile getConfigFile(@Nonnull Class<T> clazz) {
        return new XmlFile(new File(Jenkins.get().getRootDir(), clazz.getCanonicalName() + ".xml"));
    }

    /**
     * Saves a Saveable object to its default location. This collaborates with {@link BulkChange}.
     */
    public static <T extends Saveable> void save(@Nonnull T object) throws IOException {
        if (!BulkChange.contains(object)) {
            XmlFile file = getConfigFile(object.getClass());
            file.write(object);
            SaveableListener.fireOnChange(object, file);
        }
    }

    /**
     * Loads a Saveable object from its default location or returns {@code null} when the file doesn't exist.
     */
    @SuppressWarnings("unchecked")
    public static @CheckForNull <T extends Saveable> T load(@Nonnull Class<T> clazz) throws IOException {
        XmlFile file = getConfigFile(clazz);
        return file.exists() ? (T) file.read() : null;
    }

    private Persistence() {
    }
}
