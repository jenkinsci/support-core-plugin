package com.cloudbees.jenkins.support.config;

import hudson.Extension;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.Symbol;

/**
 * A {@link GlobalConfigurationCategory} for the global configuration implementation for Support Core.
 *
 * @author Allan Burdajewicz
 */
@Extension
@Symbol({"support"})
public class SupportPluginConfigurationCategory extends GlobalConfigurationCategory {
    @Override
    public String getShortDescription() {
        return com.cloudbees.jenkins.support.config.Messages.SupportPluginConfigurationCategory_shortDescription();
    }

    @Override
    public String getDisplayName() {
        return com.cloudbees.jenkins.support.config.Messages.SupportPluginConfigurationCategory_displayName();
    }
}
