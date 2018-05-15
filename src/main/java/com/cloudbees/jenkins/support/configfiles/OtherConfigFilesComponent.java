package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support component for adding xml files to the support bundle.
 */
@Extension
public class OtherConfigFilesComponent extends Component {

    private static final List<String> BLACKLISTED_FILENAMES = Arrays.asList(
            // contains anonymized content mappings
            ContentMappings.class.getName() + ".xml",
            // credentials.xml is handled by XmlRedactedSecretFileContent
            "credentials.xml",
            // config.xml is handled by ConfigFileComponent
            "config.xml"
    );

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
    public void addContents(@NonNull Container container) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            File dir = jenkins.getRootDir();
            File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".xml") && !BLACKLISTED_FILENAMES.contains(name));
            if (files != null) {
                for (File configFile : files) {
                    if (configFile.exists()) {
                        container.add(new XmlRedactedSecretFileContent("jenkins-root-configuration-files/" + configFile.getName(), configFile));
                    }
                }
            } else {
                LOGGER.log(Level.WARNING, "Cannot list files in Jenkins root, probably something is wrong with the path");
            }
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(OtherConfigFilesComponent.class.getName());

}
