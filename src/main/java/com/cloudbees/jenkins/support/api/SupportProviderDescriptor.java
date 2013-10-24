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

import hudson.model.Descriptor;

/**
 * Base class for {@link Descriptor}s of {@link SupportProvider}
 *
 * @author Stephen Connolly
 */
public abstract class SupportProviderDescriptor extends Descriptor<SupportProvider> {

    /**
     * Construct a default instance of the {@link SupportProvider}. This method is used when there is no current
     * selected {@link SupportProvider} and this provider has been selected to act as the default {@link
     * SupportProvider}
     *
     * @return the instance.
     */
    public SupportProvider newDefaultInstance() {
        try {
            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException("Failed to instantiate " + clazz, e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to instantiate " + clazz, e);
        }
    }

}
