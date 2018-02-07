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
package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.util.Anonymizer;
import hudson.Extension;
import hudson.model.ManagementLink;

import javax.annotation.CheckForNull;
import java.util.Map;

/**
 * Action for viewing anonymized items mapping.
 */
@Extension
public class AnonymizedItems extends ManagementLink {

    @CheckForNull
    @Override
    public String getIconFileName() {
        // TODO:  New icon?
        return "/plugin/support-core/images/24x24/support.png";
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return Messages.AnonymizedItems_DisplayName();
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "anonymizedItems";
    }

    @Override
    public String getDescription() {
        return Messages.AnonymizedItems_Description();
    }

    public Map<String, String> getAnon() {
        return Anonymizer.getDisplayItems();
    }
}
