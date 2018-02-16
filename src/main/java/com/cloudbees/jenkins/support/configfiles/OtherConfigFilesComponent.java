package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.ContentData;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    public void addContents(@NonNull Container container) {
        addContents(container, false);
    }

    @Override
    public void addContents(@NonNull Container container, boolean shouldAnonymize) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            File dir = jenkins.getRootDir();
            File[] files = dir.listFiles((dir1, name) -> {
                //all xml files but credentials.xml (black-listed) and config.xml (already handled by ConfigFileComponent)
                return name.toLowerCase().endsWith(".xml") && !name.equals("credentials.xml") && !name.equals("config.xml");
            });
            if (files != null) {
                for (File configFile : files) {
                    if (configFile.exists()) {
                        container.add(new XmlRedactedSecretFileContent(new ContentData("jenkins-root-configuration-files/" + configFile.getName(), shouldAnonymize), configFile));
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
