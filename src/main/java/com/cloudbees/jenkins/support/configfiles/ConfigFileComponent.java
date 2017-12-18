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
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Support component to add config.xml to the support bundle.
 */
@Extension
public class ConfigFileComponent extends Component {
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Jenkins Global Configuration File (Encrypted secrets are redacted)";
    }

    @Override
    public void addContents(@NonNull Container container) {
        Jenkins jenkins = Jenkins.getInstance();
        File configFile = new File(jenkins.getRootDir(), "config.xml");
        if (configFile.exists()) {
            container.add(
                    new XmlRedactedSecretFileContent("jenkins-root-configuration-files/" + configFile.getName(),
                                                     configFile));
        } else {
            //this should never happen..
            LOGGER.log(Level.WARNING, "Jenkins global config file does not exist.");
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(ConfigFileComponent.class.getName());
}
