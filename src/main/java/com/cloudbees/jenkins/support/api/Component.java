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

import com.cloudbees.jenkins.support.Messages;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractModelObject;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.springframework.security.core.Authentication;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.Collections;
import java.util.Set;

/**
 * Represents a component of a support bundle.
 *
 * <p>
 * This is the unit of user consent; when creating a support bundle, the user would enable/disable
 * individual components.
 *
 * @author Stephen Connolly
 */
@ExportedBean
public abstract class Component implements ExtensionPoint {
    
    /**
     * Returns the (possibly empty, never null) list of permissions that are required for the user to include this
     * in a bundle. An empty list indicates that any user can include this bundle.
     *
     * @return the (possibly empty, never null) list of permissions that are required for the user to include this
     *         in a bundle.
     */
    @NonNull
    public abstract Set<Permission> getRequiredPermissions();

    private Set<Permission> _getRequiredPermissions() {
        try {
            return getRequiredPermissions();
        } catch (AbstractMethodError x) {
            return Collections.emptySet();
        }
    }

    public String getDisplayPermissions() {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (Permission p : _getRequiredPermissions()) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(p.group.title.toString());
            buf.append('/');
            buf.append(p.name);
        }
        return buf.toString();
    }

    /**
     * Returns {@code true} if the current authentication can include this component in a bundle.
     *
     * @return {@code true} if the current authentication can include this component in a bundle.
     */
    @Exported
    public boolean isEnabled() {
        ACL acl = Jenkins.get().getAuthorizationStrategy().getRootACL();
        Authentication authentication = Jenkins.getAuthentication2();
        for (Permission p : _getRequiredPermissions()) {
            if (!acl.hasPermission2(authentication, p)) {
                return false;
            }
        }
        return true;
    }

    @Exported
    public boolean isSelectedByDefault() {
        return true;
    }

    /**
     * Return if this component is applicable to a specific class of item.
     *
     * @param clazz the class
     * @param <C> Object that extends {@link AbstractModelObject}
     * @return {@code true} if applicable to this class
     */
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz); 
    }

    @NonNull
    @Exported
    public abstract String getDisplayName();

    /**
     * Add contents to a container
     * @param container a {@link Container}
     */
    public abstract void addContents(@NonNull Container container);

    /**
     * Returns the component id.
     *
     * @return by default, the {@link Class#getSimpleName} of the component implementation.
     */
    @Exported
    @NonNull public String getId() {
        return getClass().getSimpleName();
    }

    @Deprecated
    public void start(@NonNull SupportContext context) {
    }

    /**
     * Specify in which {@link ComponentCategory} the current component is related.
     *
     * @return An enum value of {@link ComponentCategory}.
     * @since TODO
     */
    @Exported
    @NonNull
    public ComponentCategory getCategory() {
        return ComponentCategory.UNCATEGORIZED;
    }

    /**
     * Categories supported by this version of support-core
     *
     * @since TODO
     */
    public enum ComponentCategory {
        /**
         * For components related to the agents, their configuration and other bits.
         */
        AGENT(Messages._SupportPlugin_Category_Agent()),
        /**
         * For components related to the controller, its configuration and other bits.
         */
        CONTROLLER(Messages._SupportPlugin_Category_Controller()),
        /**
         * For components related to the logging system or the logs themself.
         */
        LOGS(Messages._SupportPlugin_Category_Logs()),
        /**
         * Anything that doesn't fit any other categories.
         */
        MISC(Messages._SupportPlugin_Category_Misc()),
        /**
         * For components related to how the controller is running and where it is running.
         */
        PLATFORM(Messages._SupportPlugin_Category_Platform()),
        /**
         * The default category. This shouldn't be explicitly used.
         */
        UNCATEGORIZED(Messages._SupportPlugin_Category_Uncategorized());

        private final Localizable label;

        ComponentCategory(Localizable label) {
            this.label = label;
        }

        public @NonNull String getLabel() {
            return label.toString();
        }
    }
}
