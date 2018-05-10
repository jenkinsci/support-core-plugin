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
import jenkins.model.GlobalConfiguration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Configures content filters for anonymization.
 *
 * @see ContentFilter
 * @since TODO
 */
@Extension
@Restricted(NoExternalUse.class)
public class ContentFilters extends GlobalConfiguration {

    private static final ContentFilter ALL = new AllContentFilters();
    private static final ContentFilter DISABLED = new DisabledContentFilter();

    public static ContentFilter get() {
        return ExtensionList.lookupSingleton(ContentFilters.class).isEnabled() ? ALL : DISABLED;
    }

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    @Override
    public @Nonnull String getDisplayName() {
        return Messages.ContentFilters_DisplayName();
    }

    private static class AllContentFilters implements ContentFilter {
        @Override
        public @Nonnull String filter(@Nonnull String input) {
            String filtered = input;
            for (ContentFilter filter : ContentFilter.all()) {
                filtered = filter.filter(filtered);
            }
            return filtered;
        }

        @Override
        public void reload() {
            ContentFilter.all().forEach(ContentFilter::reload);
        }
    }

    private static class DisabledContentFilter implements ContentFilter {
        @Override
        public @Nonnull String filter(@Nonnull String input) {
            return input;
        }
    }
}
