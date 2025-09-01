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
package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.security.Permission;
import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Adds nodes config.xml files to the bundle.
 * Any secret string is redacted in the xml file.
 *
 * Unselected by default.
 */
@Extension
public class AgentsConfigFile extends ObjectComponent<Computer> {

    @DataBoundConstructor
    public AgentsConfigFile() {
        super();
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public boolean isSelectedByDefault(Computer item) {
        return true;
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.AGENT;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Agent Configuration File";
    }

    @Override
    public void addContents(@NonNull Container container) {
        for (var n : Jenkins.get().getNodes()) {
            var c = n.toComputer();
            if (c != null) {
                addContents(container, c);
            }
        }
    }

    @Override
    public void addContents(@NonNull Container container, Computer item) {
        if (item.getNode() == null) {
            return;
        }
        File agentDir = new File(Jenkins.get().getRootDir(), MessageFormat.format("nodes/{0}", item.getName()));
        File config = new File(agentDir, "config.xml");
        container.add(new XmlRedactedSecretFileContent(
                "nodes/slave/{0}/config.xml", new String[] {agentDir.getName()}, config));
    }

    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz) || Computer.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean isApplicable(Computer item) {
        return !(item instanceof Jenkins.MasterComputer);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("agentsConfigFileComponent")
    public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Agent Configuration File";
        }
    }
}
