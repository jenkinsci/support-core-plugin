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
package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.util.Anonymizer;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.util.FormApply;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Map;

/**
 * Action for viewing anonymized items mapping.
 */
@Extension
public class AnonymizedItems extends ManagementLink {
    private SupportPlugin plugin;
    private SupportPlugin.AnonymizationSettings settings;

    public AnonymizedItems() {
        plugin = SupportPlugin.getInstance();
        settings = plugin.getAnonymizationSettings();
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
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

    public Map<String, String> getAnonymizedItemMap() {
        return Anonymizer.getDisplayItems();
    }

    public boolean isAnonymizeLabels() {
        return settings.isAnonymizeLabels();
    }

    public void setAnonymizeLabels(boolean anonymizeLabels) {
        settings.setAnonymizeLabels(anonymizeLabels);
    }

    public boolean isAnonymizeItems() {
        return settings.isAnonymizeItems();
    }

    public void setAnonymizeItems(boolean anonymizeItems) throws IOException {
        settings.setAnonymizeItems(anonymizeItems);
    }

    public boolean isAnonymizeViews() {
        return settings.isAnonymizeViews();
    }

    public void setAnonymizeViews(boolean anonymizeViews) throws IOException {
        settings.setAnonymizeViews(anonymizeViews);
    }

    public boolean isAnonymizeNodes() {
        return settings.isAnonymizeNodes();
    }

    public void setAnonymizeNodes(boolean anonymizeNodes) throws IOException {
        settings.setAnonymizeNodes(anonymizeNodes);
    }

    public boolean isAnonymizeComputers() {
        return settings.isAnonymizeComputers();
    }

    public void setAnonymizeComputers(boolean anonymizeComputers) throws IOException {
        settings.setAnonymizeComputers(anonymizeComputers);
    }

    public boolean isAnonymizeUsers() {
        return settings.isAnonymizeUsers();
    }

    public void setAnonymizeUsers(boolean anonymizeUsers) throws IOException {
        settings.setAnonymizeUsers(anonymizeUsers);
    }

    @RequirePOST
    public void doUpdateNow(StaplerRequest request, StaplerResponse response) throws ServletException, IOException {
        Anonymizer.refresh();
        response.forwardToPreviousPage(request);
    }

    @RequirePOST
    public synchronized void doConfigSubmit(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        JSONObject json = request.getSubmittedForm();
        setAnonymizeLabels(json.getBoolean("anonymizeLabels"));
        setAnonymizeItems(json.getBoolean("anonymizeItems"));
        setAnonymizeViews(json.getBoolean("anonymizeViews"));
        setAnonymizeNodes(json.getBoolean("anonymizeNodes"));
        setAnonymizeComputers(json.getBoolean("anonymizeComputers"));
        setAnonymizeUsers(json.getBoolean("anonymizeUsers"));

        plugin.save();

        FormApply.success(".").generateResponse(request, response, null);
    }
}
