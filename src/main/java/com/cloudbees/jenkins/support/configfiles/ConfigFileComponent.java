package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
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
        return "Jenkins Global Configuration File";
    }

    @Override
    public void addContents(@NonNull Container container) {
        Jenkins jenkins = Jenkins.getInstance();
        if(jenkins != null) {
            File configFile = new File(jenkins.getRootDir(), "config.xml");
            if (configFile.exists()) {
                try {
                    File patchedXmlFile = SecretHandler.findSecrets(configFile);
                    container.add(new FileContent("jenkins-root-configuration-files/" + configFile.getName(), patchedXmlFile));
                } catch (IOException | ParserConfigurationException | XMLStreamException | SAXException | TransformerException e) {
                    logger.log(Level.WARNING, "could not add the {0} configuration file to the support bundle because of: {1}", new Object[]{configFile.getName(), e});
                }
            } else {
                //this should never happen..
                logger.log(Level.WARNING, "Jenkins global config file does not exist.");
            }
        }
    }

    private static final Logger logger = Logger.getLogger(ConfigFileComponent.class.getName());
}
