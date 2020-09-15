package com.cloudbees.jenkins.support.config;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.security.Permission;
import hudson.util.FormApply;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ManagementLink} for the management of Support Core. This extension also manages the GUI for the 
 * {@link jenkins.model.GlobalConfiguration} under the {@link SupportPluginConfigurationCategory}.
 * 
 * @author Allan Burdajewicz
 */
@Extension
@Restricted(NoExternalUse.class)
@Symbol("supportCore")
public class SupportPluginManagement extends ManagementLink implements Describable<SupportPluginManagement>, Saveable {

    private static final Logger LOG = Logger.getLogger(SupportPluginManagement.class.getName());

    public final static Predicate<Descriptor> CATEGORY_FILTER = descriptor -> descriptor.getCategory() instanceof SupportPluginConfigurationCategory;

    /**
     * Gets the singleton instance.
     *
     * @return Singleton instance
     */
    public static @NonNull
    SupportPluginManagement get() {
        return ExtensionList.lookupSingleton(SupportPluginManagement.class);
    }

    public SupportPluginManagement() {
        load();
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return "/plugin/support-core/images/48x48/support.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.SupportPluginManagement_displayName();
    }

    @Override
    public String getUrlName() {
        return "supportCore";
    }

    @Override
    public String getDescription() {
        return Messages.SupportPluginManagement_description();
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    // TODO Use getCategory when core requirement is greater or equal to 2.226
    public @NonNull
    String getCategoryName() {
        return "CONFIGURATION";
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), getClass().getName() + ".xml"));
    }

    public synchronized void load() {
        XmlFile file = getConfigFile();
        if (!file.exists()) {
            return;
        }

        try {
            file.unmarshal(this);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load " + file, e);
        }
    }

    @Override
    public void save() throws IOException {
        if (BulkChange.contains(this)) {
            return;
        }
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
        }
    }
    
    @POST
    public synchronized void doConfigure(StaplerRequest req, StaplerResponse rsp)
        throws IOException, ServletException, Descriptor.FormException {

        configure(req, req.getSubmittedForm());
        FormApply.success(req.getContextPath() + "/manage").generateResponse(req, rsp, null);
    }

    public boolean configure(StaplerRequest req, JSONObject json) throws Descriptor.FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        boolean result = true;
        for (Descriptor<?> d : getDescriptors()) {
            result &= configureDescriptor(req, json, d);
        }

        return result;
    }

    private boolean configureDescriptor(StaplerRequest req, JSONObject json, Descriptor<?> d)
        throws Descriptor.FormException {
        String name = d.getJsonSafeClassName();
        JSONObject js = json.has(name) ? json.getJSONObject(name) : new JSONObject();
        json.putAll(js);
        return d.configure(req, js);
    }

    @NonNull
    public Collection<Descriptor> getDescriptors() {
        return Functions.getSortedDescriptorsForGlobalConfigByDescriptor(CATEGORY_FILTER);
    }

    /**
     * @return
     * @see Describable#getDescriptor()
     */
    @Override
    public Descriptor<SupportPluginManagement> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Extension
    @Symbol("supportCore")
    public static final class DescriptorImpl extends Descriptor<SupportPluginManagement> {

        @Override
        public String getDisplayName() {
            return com.cloudbees.jenkins.support.config.Messages.SupportPluginManagement_displayName();
        }
    }
}
