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

package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.security.Permission;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Support component for adding xml files to the support bundle.
 */
@Extension
public class OtherConfigFilesComponent extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Other Jenkins Configuration Files (Encrypted secrets are redacted)";
    }

    @Override
    public int getHash() {
        return 27;
    }

    @Override
    public void addContents(@NonNull Container container) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            File dir = jenkins.getRootDir();
            File[] files = dir.listFiles((pathname) -> ConfigFilesFilter.all().stream()
                    .allMatch(configFilesFilter -> configFilesFilter.include(pathname)));
            if (files != null) {
                for (File configFile : files) {
                    if (configFile.exists()) {
                        container.add(new XmlRedactedSecretFileContent(
                                "jenkins-root-configuration-files/{0}",
                                new String[] {configFile.getName()}, configFile));
                    }
                }
            } else {
                LOGGER.log(
                        Level.WARNING, "Cannot list files in Jenkins root, probably something is wrong with the path");
            }
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    private static final Logger LOGGER = Logger.getLogger(OtherConfigFilesComponent.class.getName());

    /**
     * Extension to contribute to the list of configuration files to filter. A file need to be accepted by all
     * {@link ConfigFilesFilter} implementations to be included in the content.
     */
    public interface ConfigFilesFilter extends ExtensionPoint {

        /**
         * @return all {@link ConfigFilesFilter} extensions
         */
        static ExtensionList<ConfigFilesFilter> all() {
            return ExtensionList.lookup(ConfigFilesFilter.class);
        }

        /**
         * Return whether the {@link java.io.File} passed in should be included.
         * @param file the {@link java.io.File}
         * @return true to include or false to exclude
         */
        boolean include(@NonNull File file);
    }

    @Extension
    public static class DefaultConfigFilesFilter implements ConfigFilesFilter {

        private static final List<String> BLACKLISTED_FILENAMES = List.of(
                // contains anonymized content mappings
                ContentMappings.class.getName() + ".xml",
                // credentials.xml contains rather sensitive data
                "credentials.xml",
                // config.xml is handled by ConfigFileComponent
                "config.xml");

        @Override
        public boolean include(@NonNull File f) {
            return f.getName().toLowerCase().endsWith(".xml") && !BLACKLISTED_FILENAMES.contains(f.getName());
        }
    }
}
