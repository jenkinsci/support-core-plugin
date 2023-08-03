package com.cloudbees.jenkins.support.config;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.logging.Level.WARNING;

/**
 * Global Configuration for the Automated Support Bundle Generation, see {@link SupportPlugin.PeriodicWorkImpl}.
 * 
 * @author Allan Burdajewicz
 */
@Extension
@Restricted(NoExternalUse.class)
@Symbol("automatedBundleConfiguration")
public class SupportAutomatedBundleConfiguration extends GlobalConfiguration {

    private static final Logger LOG = Logger.getLogger(SupportAutomatedBundleConfiguration.class.getName());

    /**
     * Gets the singleton instance.
     *
     * @return Singleton instance
     */
    public static @NonNull
    SupportAutomatedBundleConfiguration get() {
        return ExtensionList.lookupSingleton(SupportAutomatedBundleConfiguration.class);
    }

    /**
     * The list of components configured for background generation.
     */
    private List<String> componentIds;

    /**
     * Whether to enable Support Bundle background generation or not.
     */
    private boolean enabled = true;

    /**
     * How often automatic support bundles should be collected. Should be {@code 1} unless you have very good reason
     * to use a different period. {@code 0} disables bundle generation and {@code 24} is the longest period permitted.
     */
    private int period = 1;

    public SupportAutomatedBundleConfiguration() {
        load();
    }

    public List<String> getComponentIds() {
        return componentIds == null
            ? getDefaultComponentIds()
            : componentIds;
    }

    /**
     * Get the default list of Component Ids for the automated bundle generation.
     *
     * @return a list of {@link String}
     */
    public static List<String> getDefaultComponentIds() {
        return getApplicableComponents()
            .stream()
            .filter(Component::isEnabled)
            .map(Component::getId)
            .collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return !isEnforcedDisabled() && (isEnforcedPeriod() || enabled);
    }

    public int getPeriod() {
        return isEnforcedDisabled() ? 0
            : Math.max(Math.min(24, isEnforcedPeriod() ? SupportPlugin.AUTO_BUNDLE_PERIOD_HOURS : period), 1);
    }

    /**
     * Return if the {@link SupportAutomatedBundleConfiguration#period} is enforced by the System Property
     * {@link SupportPlugin#AUTO_BUNDLE_PERIOD_HOURS}.
     *
     * @return true if enforced by system property, false otherwise
     */
    public boolean isEnforcedPeriod() {
        return System.getProperty(SupportPlugin.class.getName() + ".AUTO_BUNDLE_PERIOD_HOURS") != null;
    }

    @DataBoundSetter
    public void setComponentIds(List<String> componentIds) {
        this.componentIds = componentIds;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Return if the {@link SupportAutomatedBundleConfiguration#enabled} is enforced to false by the System Property
     * {@link SupportPlugin#AUTO_BUNDLE_PERIOD_HOURS}. That is if the system property value is 0.
     *
     * @return true if enforced by system property, false otherwise
     */
    public boolean isEnforcedDisabled() {
        return SupportPlugin.AUTO_BUNDLE_PERIOD_HOURS == 0;
    }

    @DataBoundSetter
    public void setPeriod(Integer period) {
        this.period = Math.max(Math.min(24, period), 1);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return Messages.SupportAutomatedBundleConfiguration_displayName();
    }

    /**
     * Get the list of {@link Component} currently configured for automated bundle generation.
     *
     * @return The list of {@link Component} currently configured
     */
    public List<Component> getComponents() {
        return componentIds == null
            ? getApplicableComponents().stream()
            .filter(component -> getDefaultComponentIds().contains(component.getId()))
            .collect(Collectors.toList())
            : getApplicableComponents().stream()
            .filter(component -> componentIds.contains(component.getId()))
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unused") // used by Jelly
    public boolean isComponentSelected(Component component) {
        return componentIds == null || componentIds.stream().anyMatch(c -> c.equals(component.getId()));
    }

    /**
     * Get the list of applicable (and therefore selectable) {@link Component} for the automated bundle generation.
     *
     * @return a list of {@link Component}
     */
    public static List<Component> getApplicableComponents() {
        return SupportPlugin.getComponents();
    }

    @Override
    @POST
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if(isEnforcedDisabled()) {
            return true;
        }

        boolean isEnabled = isEnforcedPeriod() || json.getBoolean("enabled");

        if (isEnabled && !json.has("components")) {
            throw new Descriptor.FormException(Messages.SupportAutomatedBundleConfiguration_enabled_noComponents(),
                "components");
        }
        try (BulkChange bc = new BulkChange(this)) {
            setComponentIds(parseRequest(req, json));
            if (!isEnforcedPeriod()) {
                setPeriod(json.getInt("period"));
            }
            setEnabled(json.getBoolean("enabled"));
            bc.commit();
        } catch (IOException e) {
            LOG.log(WARNING, "Failed to save " + getConfigFile(), e);
        }
        return true;
    }

    /**
     * Parse the stapler JSON output and retrieve configured components.
     *
     * @param req the request
     * @return the {@link DescribableList} of components
     */
    protected final List<String> parseRequest(StaplerRequest req, JSONObject json) {
        Set<String> remove = new HashSet<>();
        for (SupportAction.Selection s : req.bindJSONToList(SupportAction.Selection.class, json.get("components"))) {
            if (!s.isSelected()) {
                remove.add(s.getName());
            }
        }
        return getApplicableComponents().stream()
            .filter(component -> !remove.contains(component.getId()) && component.isEnabled())
            .map(Component::getId)
            .collect(Collectors.toList());
    }

    @Override
    public @NonNull
    GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(SupportPluginConfigurationCategory.class);
    }

    @SuppressWarnings("unused") // jelly
    public FormValidation doCheckPeriod(@QueryParameter String value) {
        if (value == null) {
            return FormValidation.error(Messages.SupportAutomatedBundleConfiguration_period_mustBeSpecified());
        }

        int i;
        try {
            i = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return FormValidation.error(Messages.SupportAutomatedBundleConfiguration_period_isNotAnInteger(), e);
        }

        if (i < 1) {
            return FormValidation.error(Messages.SupportAutomatedBundleConfiguration_period_lowerThanMin());
        }

        if (i > 24) {
            return FormValidation.error(Messages.SupportAutomatedBundleConfiguration_period_greaterThanMax());
        }

        return FormValidation.ok();
    }
}
