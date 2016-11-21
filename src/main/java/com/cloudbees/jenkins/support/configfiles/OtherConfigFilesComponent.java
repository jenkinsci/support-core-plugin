package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.util.Helper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
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
        return "Other Jenkins Configuration Files";
    }

    @Override
    public void addContents(@NonNull Container container) {
        Jenkins jenkins = Helper.getActiveInstance();
        if (jenkins != null) {
            File dir = jenkins.getRootDir();
            File[] files = dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    //all xml files but credentials.xml (black-listed) and config.xml (already handled by ConfigFileComponent)
                    return name.toLowerCase().endsWith(".xml") && !name.equals("credentials.xml") && !name.equals("config.xml");
                }
            });
            if (files != null) {
                for (File configFile : files) {
                    if (configFile.exists()) {
                        try {
                            File patchedXmlFile = SecretHandler.findSecrets(configFile);
                            container.add(new FileContent("jenkins-root-configuration-files/" + configFile.getName(), patchedXmlFile));
                        } catch (IOException | ParserConfigurationException | XMLStreamException | SAXException | TransformerException e) {
                            LOGGER.log(Level.WARNING, "could not add the {0} configuration file to the support bundle because of: {1}", new Object[]{configFile.getName(), e});
                        }
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
