package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.BundleFileName;
import com.cloudbees.jenkins.support.Messages;
import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Support Action to Generate Support bundle from the UI and REST APIs.
 */
@Restricted(NoExternalUse.class)
public class SupportBundleAction extends SupportChildAction {

    private static final Logger LOGGER = Logger.getLogger(SupportBundleAction.class.getName());

    static final String URL = "bundle";

    public SupportBundleAction(@NonNull SupportAction supportAction) {
        super(supportAction);
    }

    @Override
    public String getIconFileName() {
        return "/plugin/support-core/images/support.svg";
    }

    @Override
    public String getDisplayName() {
        return com.cloudbees.jenkins.support.actions.Messages.SupportBundleAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return URL;
    }

    @SuppressWarnings("unused") // used by Jelly
    public String getActionTitleText() {
        return getActionTitle().toString();
    }

    @SuppressWarnings("unused") // used by Jelly
    public Localizable getActionTitle() {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            SupportProvider supportProvider = supportPlugin.getSupportProvider();
            if (supportProvider != null) {
                return supportProvider.getActionTitle();
            }
        }
        return Messages._SupportAction_DefaultActionTitle();
    }

    @SuppressWarnings("unused") // used by Jelly
    public Localizable getActionBlurb() {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            SupportProvider supportProvider = supportPlugin.getSupportProvider();
            if (supportProvider != null) {
                return supportProvider.getActionBlurb();
            }
        }
        return Messages._SupportAction_DefaultActionBlurb();
    }

    @SuppressWarnings("unused") // used by Jelly
    public Map<Component.ComponentCategory, List<Component>> getCategorizedComponents() {
        return Jenkins.get().getExtensionList(Component.class)
            .stream()
            .filter(component -> component.isApplicable(Jenkins.class))
            .collect(Collectors.groupingBy(Component::getCategory, Collectors.toList()))
            .entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Component.ComponentCategory::getLabel)))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .sorted(Comparator.comparing(Component::getDisplayName))
                    .collect(Collectors.toCollection(LinkedList::new)),
                (e1, e2) -> e2,
                LinkedHashMap::new
            ));
    }

    public boolean isAnonymized() {
        return ContentFilters.get().isEnabled();
    }

    /**
     * Generates a support bundle with selected components from the UI.
     *
     * @param req The stapler request
     * @param rsp The stapler response
     * @throws ServletException If an error occurred during form submission
     * @throws IOException      If an input or output exception occurs
     */
    @RequirePOST
    @SuppressWarnings("unused") // used by Jelly
    public void doGenerateAllBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONObject json = req.getSubmittedForm();
        if (!json.has("components")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        LOGGER.fine("Parsing request...");
        Set<String> remove = new HashSet<>();
        for (SupportAction.Selection s : req.bindJSONToList(SupportAction.Selection.class, json.get("components"))) {
            if (!s.isSelected()) {
                LOGGER.log(Level.FINER, "Excluding ''{0}'' from list of components to include", s.getName());
                remove.add(s.getName());
                // JENKINS-63722: If "Master" or "Agents" are unselected, show a warning and add the new names for 
                // those components to the list of unselected components for backward compatibility
                if ("Master".equals(s.getName()) || "Agents".equals(s.getName())) {
                    LOGGER.log(Level.WARNING, Messages._SupportCommand_jenkins_63722_deprecated_ids(s.getName()).toString());
                    remove.add(s.getName() + "JVMProcessSystemMetricsContents");
                    remove.add(s.getName() + "SystemConfiguration");
                }
            }
        }
        LOGGER.fine("Selecting components...");
        final List<Component> components = new ArrayList<>(SupportPlugin.getComponents());
        components.removeIf(c -> remove.contains(c.getId()) || !c.isEnabled());
        final SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            supportPlugin.setExcludedComponents(remove);
        }
        prepareBundle(rsp, components);
    }

    /**
     * Generates a support bundle with only requested components.
     *
     * @param components component names separated by comma.
     * @param rsp        The stapler response
     * @throws IOException If an input or output exception occurs
     */
    @RequirePOST
    @SuppressWarnings("unused") // used by Jelly
    public void doGenerateBundle(@QueryParameter("components") String components, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (components == null) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "components parameter is mandatory");
            return;
        }
        Set<String> componentNames = Arrays
            .stream(components.split(","))
            .collect(Collectors.toSet());

        // JENKINS-63722: If "Master" or "Agents" are used, show a warning and add the new names for those components
        // to the selection for backward compatibility
        if (componentNames.contains("Master")) {
            LOGGER.log(Level.WARNING, Messages._SupportCommand_jenkins_63722_deprecated_ids("Master").toString());
            componentNames.add("MasterJVMProcessSystemMetricsContents");
            componentNames.add("MasterSystemConfiguration");
        }
        if (componentNames.contains("Agents")) {
            LOGGER.log(Level.WARNING, Messages._SupportCommand_jenkins_63722_deprecated_ids("Agents").toString());
            componentNames.add("AgentsJVMProcessSystemMetricsContents");
            componentNames.add("AgentsSystemConfiguration");
        }

        LOGGER.fine("Selecting components...");
        List<Component> selectedComponents = SupportPlugin.getComponents().stream().filter(c -> componentNames.contains(c.getId())).collect(Collectors.toList());
        if (selectedComponents.isEmpty()) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "selected component list is empty");
            return;
        }
        prepareBundle(rsp, selectedComponents);
    }

    private void prepareBundle(StaplerResponse rsp, List<Component> components) throws IOException {
        LOGGER.fine("Preparing response...");
        rsp.setContentType("application/zip");
        rsp.addHeader("Content-Disposition", "inline; filename=" + BundleFileName.generate() + ";");
        final ServletOutputStream servletOutputStream = rsp.getOutputStream();
        try {
            SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication2());
            try {
                try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                    SupportPlugin.writeBundle(servletOutputStream, components);
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, e.getMessage(), e);
                }
            } finally {
                SupportPlugin.clearRequesterAuthentication();
            }
        } finally {
            LOGGER.fine("Response completed");
        }
    }

    @SuppressWarnings("unused") // used by Jelly
    public boolean selectedByDefault(Component c) {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        return c.isSelectedByDefault() && (supportPlugin == null || !supportPlugin.getExcludedComponents().contains(c.getId()));
    }
}
