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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

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
    public boolean isEnabled() {
        ACL acl = Jenkins.getInstance().getAuthorizationStrategy().getRootACL();
        if (acl != null) {
            Authentication authentication = Jenkins.getAuthentication();
            assert authentication != null;
            for (Permission p : _getRequiredPermissions()) {
                if (!acl.hasPermission(authentication, p)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isSelectedByDefault() {
        return true;
    }

    @NonNull
    public abstract String getDisplayName();

    /**
     * Returns the component id.
     *
     * @return by default, the {@link Class#getSimpleName} of the component implementation.
     */
    @NonNull public String getId() {
        return getClass().getSimpleName();
    }

    public abstract void addContents(@NonNull Container container);

    public void start(@NonNull SupportContext context) {

    }
}
