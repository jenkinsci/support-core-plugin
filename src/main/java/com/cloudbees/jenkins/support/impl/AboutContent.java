package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.model.About;
import com.cloudbees.jenkins.support.util.Helper;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.lifecycle.Lifecycle;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by schristou88 on 2/9/17.
 */
public class AboutContent {
    protected About generate() {
        About about = new About();
        About.VersionDetails vd = new GetJavaInfo().call();
        about.setVersionDetails(generateVersionDetails(vd));

        about.setImportantConfiguration(generateImportantConfiguration());
        about.setActivePlugins(getActivePlugins());
        return about;
    }

    private About.ActivePlugins getActivePlugins() {
        About.ActivePlugins ap = new About.ActivePlugins();
        PluginManager pluginManager = Helper.getActiveInstance().getPluginManager();
        List<PluginWrapper> plugins = new ArrayList<>(pluginManager.getPlugins());
        Collections.sort(plugins);
        for (PluginWrapper w : plugins) {
            if (w.isActive()) {
                About.ActivePlugins.Plugin p = new About.ActivePlugins.Plugin();
                p.setName(w.getShortName());
                p.setVersion(w.getVersion());
                p.setUpdates_available(w.hasUpdate());
                p.setDescription(w.getLongName());
                ap.addPlugin(p);
            }
        }

        return ap;
    }

    private About.VersionDetails generateVersionDetails(About.VersionDetails versionDetails) {
        versionDetails.setVersion(Jenkins.getVersion().toString());
        File jenkinsWar = Lifecycle.get().getHudsonWar();

        if (jenkinsWar == null) {
            versionDetails.setMode("Webapp Directory");
        } else {
            versionDetails.setMode("WAR");
        }

        final JenkinsLocationConfiguration jlc = JenkinsLocationConfiguration.get();
        if (jlc != null) {
            versionDetails.setUrl(jlc.getUrl());
        }

        versionDetails.setContainer(generateServletContainer());

        return versionDetails;
    }

    private About.VersionDetails.ServletContainer generateServletContainer() {
        About.VersionDetails.ServletContainer sc = new About.VersionDetails.ServletContainer();
        try {
            final ServletContext servletContext = Stapler.getCurrent().getServletContext();
            sc.setSpecification(servletContext.getMajorVersion() + "." + servletContext.getMinorVersion());
            sc.setName(servletContext.getServerInfo());
        } catch (NullPointerException e) {
            // pity Stapler.getCurrent() throws an NPE when outside of a request
        }
        return sc;
    }

    private About.ImportantConfiguration generateImportantConfiguration() {
        About.ImportantConfiguration ic = new About.ImportantConfiguration();
        Jenkins jenkins = Helper.getActiveInstance();
        ic.setSecurityRealm(jenkins.getSecurityRealm().toString());
        ic.setAuthorizationStrategy(jenkins.getAuthorizationStrategy().toString());
        ic.setCSRF_protection(jenkins.isUseCrumbs());
        ic.setInitializationMilestone(jenkins.getInitLevel().toString());
        return ic;
    }
}
