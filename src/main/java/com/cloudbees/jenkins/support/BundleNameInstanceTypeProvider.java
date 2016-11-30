/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.SupportProvider;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Extension point allowing to customize the support bundle naming strategy.</p>
 * <p>
 * It will work the following way:
 * </p>
 * <ol>
 * <li>If an implementation of {@link BundleNameInstanceTypeProvider} is found, it will be used.<br>
 * <strong>WARNING: </strong>if many are found, then a warning will be issued, and the first extension found will
 * be used.</li>
 * <li>If not, then it will check for the presence of the {@link #SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY}
 * system property, and will use its value if provided.</li>
 * <li>If not, then will fallback to the original behaviour, which is simply an empty String</li>
 * </ol>
 *
 * @see SupportProvider#getName() for prefixing.
 */
public abstract class BundleNameInstanceTypeProvider implements ExtensionPoint {

    @VisibleForTesting
    static final String SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY = SupportPlugin.class.getName() + ".instanceType";

    private static final Logger LOGGER = Logger.getLogger(BundleNameInstanceTypeProvider.class.getName());

    @Nonnull
    static BundleNameInstanceTypeProvider getInstance() {
        final ExtensionList<BundleNameInstanceTypeProvider> all = ExtensionList.lookup(BundleNameInstanceTypeProvider.class);
        final int extensionCount = all.size();

        if (extensionCount > 2) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "{0} implementations found for support bundle prefix naming strategy. " +
                        "Can be only 1 (default one) or 2 (default one, plus alternative). " +
                        "Choosing the first found among the following:", extensionCount);
                for (BundleNameInstanceTypeProvider nameProvider : all) {
                    LOGGER.log(Level.WARNING, "Class {0} found", nameProvider.getClass().getName());
                }
            }
        }

        final BundleNameInstanceTypeProvider chosen = all.get(0);
        if (extensionCount > 1) {
            LOGGER.log(Level.INFO, "Using {0} as BundleNameInstanceTypeProvider implementation", chosen.getClass().getName());
        }
        return chosen;
    }

    /**
     * Returns the <strong>non-null</strong> instance type to be used for generated support bundle names.
     * Aims to provide informational data about the generated bundles.
     * <p>
     * <p>
     * <p><b>Will be used for file name generation, so avoid funky characters.
     * Please stay in <code>[a-zA-Z-_]</code></b>. Also consider the file name length, you probably want to be defensive
     * and not return crazily long strings. Something below 20 characters or so might sound reasonable.</p>
     *
     * @return the instance type specification to be used for generated support bundles.
     */
    @Nonnull
    public abstract String getInstanceType();

    // We want this to be always picked up last in case others are found Double.MIN_VALUE does NOT work
    @Extension(ordinal = -1000000)
    public static final class DEFAULT_STRATEGY extends BundleNameInstanceTypeProvider {

        @Override
        public String getInstanceType() {
            return System.getProperty(BundleNameInstanceTypeProvider.SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY, "");
        }
    }
}
