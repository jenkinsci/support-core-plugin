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

import com.cloudbees.jenkins.support.actions.SupportChildAction;
import com.cloudbees.jenkins.support.actions.SupportGenerateBundleAction;
import com.cloudbees.jenkins.support.actions.SupportGeneratedContentAction;
import com.cloudbees.jenkins.support.api.Component;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Actionable;
import hudson.model.Api;
import hudson.model.RootAction;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Parent action of Support Root actions. Also holds the REST API at /{@value URL}/api.
 */
@Extension
@ExportedBean
public class SupportAction extends Actionable implements RootAction, StaplerProxy {

    /**
     * @deprecated see {@link SupportPlugin#CREATE_BUNDLE}
     */
    @Deprecated
    public static final Permission CREATE_BUNDLE = SupportPlugin.CREATE_BUNDLE;

    static final String URL = "support";

    public static SupportAction get() {
        return ExtensionList.lookup(RootAction.class).get(SupportAction.class);
    }

    /** Support Actions */
    private final SupportGenerateBundleAction generateBundleAction;
    private final SupportGeneratedContentAction generatedContentAction;

    public SupportAction() {
        generateBundleAction = new SupportGenerateBundleAction(this);
        generatedContentAction = new SupportGeneratedContentAction(this);
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return this;
    }
    
    public String getIconFileName() {
        return "/plugin/support-core/images/support.svg";
    }

    public String getDisplayName() {
        return Messages.SupportAction_DisplayName();
    }

    public String getUrlName() {
        return URL;
    }

    @Override
    public String getSearchUrl() {
        return URL;
    }

    /**
     * Get the list of all available components.
     * Intended for REST API, for example {@code /{@value URL}/api/json?tree=components[id]}.
     *
     * @see SupportPlugin#getComponents()
     * @return a list of {@link Component}
     */
    @SuppressWarnings("unused") // used by Jelly
    @Exported
    @WebMethod(name = "components")
    public List<Component> getComponents() {
        return SupportPlugin.getComponents();
    }


    /**
     * Get {@link Component}s grouped by {@link com.cloudbees.jenkins.support.api.Component.ComponentCategory}
     *
     * @return a map of {@link Component}s per {@link com.cloudbees.jenkins.support.api.Component.ComponentCategory}
     *
     * @deprecated use {@link SupportGenerateBundleAction#getCategorizedComponents()}
     * @see SupportGenerateBundleAction#getCategorizedComponents()
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // used by Jelly
    public Map<Component.ComponentCategory, List<Component>> getCategorizedComponents() {
        return generateBundleAction.getCategorizedComponents();
    }

    /**
     * Remote API access.
     */
    public final Api getApi() {
        return new Api(this);
    }

    /**
     * @return true if anonymization is enabled, false otherwise
     *
     * @deprecated use {@link com.cloudbees.jenkins.support.filter.ContentFilters#isEnabled()}
     * @see SupportGenerateBundleAction#isAnonymized()
     */
    @Deprecated
    @Restricted(NoExternalUse.class) // Jelly
    @SuppressWarnings("unused") // used by Jelly
    public boolean isAnonymized() {
        return generateBundleAction.isAnonymized();
    }

    /**
     * Generates a support bundle with selected components from the UI.
     * @param req The stapler request
     * @param rsp The stapler response
     * @throws ServletException If an error occurred during form submission
     * @throws IOException If an input or output exception occurs
     *
     * @deprecated use {@link SupportGenerateBundleAction#doGenerateAllBundles(StaplerRequest, StaplerResponse)}
     * @see SupportGenerateBundleAction#doGenerateAllBundles(StaplerRequest, StaplerResponse)
     */
    @Deprecated
    @RequirePOST
    @Restricted(NoExternalUse.class) // Jelly
    public void doDownload(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        generateBundleAction.doGenerateAllBundles(req, rsp);
    }

    /**
     * Generates a support bundle with selected components from the UI.
     * @param req The stapler request
     * @param rsp The stapler response
     * @throws ServletException If an error occurred during form submission
     * @throws IOException If an input or output exception occurs
     *
     * @deprecated use {@link SupportGenerateBundleAction#doGenerateAllBundles(StaplerRequest, StaplerResponse)}
     * @see SupportGenerateBundleAction#doGenerateAllBundles(StaplerRequest, StaplerResponse)
     */
    @Deprecated
    @RequirePOST
    @Restricted(NoExternalUse.class) // Jelly
    public void doGenerateAllBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        generateBundleAction.doGenerateAllBundles(req, rsp);
    }

    /**
     * Generates a support bundle at /{@value URL}/generateBundle with only requested components.
     * @param components component names separated by comma.
     * @param rsp The stapler response
     * @throws IOException If an input or output exception occurs
     */
    @RequirePOST
    @Restricted(NoExternalUse.class) // Jelly
    @SuppressWarnings("unused") // REST API
    public void doGenerateBundle(@QueryParameter("components") String components, StaplerResponse rsp) throws IOException {
        generateBundleAction.doGenerateBundle(components, rsp);
     }

    /**
     * Check if a component is selected by default.
     *
     * @param c the {@link Component}
     * @return true if the component is selected by default, false otherwise
     * @deprecated use {@link SupportGenerateBundleAction#selectedByDefault(Component)}
     * @see SupportGenerateBundleAction#selectedByDefault(Component)
     */
    @Deprecated
    @Restricted(NoExternalUse.class) // Jelly
    @SuppressWarnings("unused") // used by Jelly
    public boolean selectedByDefault(Component c) {
        return generateBundleAction.selectedByDefault(c);
    }

    /**
     * Return the default action to display when hitting the Support root action.
     * @return the default {@link SupportChildAction}
     */
    @Restricted(NoExternalUse.class) // Jelly
    @SuppressWarnings("unused") // used by Jelly
    public SupportChildAction getDefaultChildAction() {
        return generateBundleAction;
    }

    public static class Selection {
        /** @see Component#getId */
        private final String name;
        private final boolean selected;

        @DataBoundConstructor
        public Selection(String name, boolean selected) {
            this.name = name;
            this.selected = selected;
        }

        public String getName() {
            return name;
        }

        public boolean isSelected() {
            return selected;
        }
    }
}
