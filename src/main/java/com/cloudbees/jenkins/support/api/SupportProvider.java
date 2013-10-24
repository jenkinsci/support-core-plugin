/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

package com.cloudbees.jenkins.support.api;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import org.jvnet.localizer.Localizable;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * The provider of support.
 *
 * @author Stephen Connolly
 */
public abstract class SupportProvider extends AbstractDescribableImpl<SupportProvider> implements ExtensionPoint {

    /**
     * Returns the name of the provider. This name will be used for the download bundles,
     * so no spaces or other silly characters.
     *
     * @return the name of the provider
     */
    public abstract String getName();

    /**
     * Returns the display name of the provider.
     *
     * @return the display name of the provider.
     */
    public abstract String getDisplayName();

    /**
     * Returns the title to put on the support action page.
     *
     * @return the title to put on the support action page.
     */
    public abstract Localizable getActionTitle();

    /**
     * Returns the blurb to put on the support action page.
     *
     * @return the blurb to put on the support action page.
     */
    public abstract Localizable getActionBlurb();

    /**
     * Allows a provider to print additional information about Jenkins into the {@code about.md} file.
     *
     * @param out the print writer.
     * @throws IOException if things go wrong.
     */
    public abstract void printAboutJenkins(PrintWriter out) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public SupportProviderDescriptor getDescriptor() {
        return (SupportProviderDescriptor) super.getDescriptor();
    }
}
