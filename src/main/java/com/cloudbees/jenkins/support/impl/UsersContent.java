/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.security.LastGrantedAuthoritiesProperty;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
public class UsersContent extends UnfilteredFileListCapComponent {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "User Count";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new PrintedContent("/users/count.md") {
            @Override
            protected void printTo(PrintWriter out) {
                final Jenkins jenkins = Jenkins.getInstanceOrNull();
                if (jenkins == null) {
                    return;
                }

                User.getAll().stream()
                    .collect(
                        Collectors.groupingBy(
                            (user) -> Optional.ofNullable(user.getProperty(LastGrantedAuthoritiesProperty.class)).isPresent(),
                            Collectors.counting()))
                        .forEach((lastGrantedAuthoritiesProperty, aLong) ->
                            out.println(" * "
                                    + (lastGrantedAuthoritiesProperty ? "Authenticated" : "Non Authenticated")
                                    + " Users count: " + aLong)
                        );
            }

            @Override
            public boolean shouldBeFiltered() {
                return false;
            }
        });
    }

    @Override
    public boolean isSelectedByDefault() {
        return true;
    }
}
