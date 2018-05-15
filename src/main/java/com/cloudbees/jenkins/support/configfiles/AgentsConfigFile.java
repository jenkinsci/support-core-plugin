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

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Adds nodes config.xml files to the bundle.
 * Any secret string is redacted in the xml file.
 *
 * Unselected by default.
 */
@Extension
public class AgentsConfigFile extends Component {

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return Messages.AgentsConfigFile_displayName();
    }

    @Override
    public void addContents(@NonNull Container container) {
        File[] agentDirs = new File(Jenkins.get().getRootDir(), "nodes").listFiles();
        if (agentDirs == null) {
            return;
        }
        for(File agentDir : agentDirs) {
            File config = new File(agentDir, "config.xml");
            container.add(new XmlRedactedSecretFileContent("nodes/slave/" + agentDir.getName() + "/config.xml", config));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AgentsConfigFile.class.getName());
}