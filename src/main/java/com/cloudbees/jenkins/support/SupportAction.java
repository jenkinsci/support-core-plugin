/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import hudson.Extension;
import hudson.model.Api;
import hudson.model.RootAction;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Main root action for generating support.
 */
@Extension
@ExportedBean
public class SupportAction implements RootAction, StaplerProxy {

    /**
     * @deprecated see {@link SupportPlugin#CREATE_BUNDLE}
     */
    @Deprecated
    public static final Permission CREATE_BUNDLE = SupportPlugin.CREATE_BUNDLE;
    /**
     * Our logger (retain an instance ref to avoid classloader leaks).
     */
    private final Logger logger = Logger.getLogger(SupportAction.class.getName());

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return this;
    }
    
    public String getIconFileName() {
        return "/plugin/support-core/images/support.svg";
    }

    public String getDisplayName() {
        return Messages.SupportAction_DisplayName();
    }

    public String getUrlName() {
        return "support";
    }

    public String getActionTitleText() {
        return getActionTitle().toString();
    }

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
    @Exported
    @WebMethod(name = "components")
    public List<Component> getComponents() {
        return SupportPlugin.getComponents();
    }

    // for Jelly
    @Restricted(NoExternalUse.class)
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

    public List<String> getBundles() {
        List<String> res = new ArrayList<>();
        File rootDirectory = SupportPlugin.getRootDirectory();
        File[] bundlesFiles = rootDirectory.listFiles((dir, name) -> name.endsWith(".zip") || name.endsWith(".log"));
        if (bundlesFiles != null) {
            for (File bundleFile : bundlesFiles) {
                res.add(bundleFile.getName());
            }
        }
        Collections.sort(res);
        return res;
    }

    /**
     * Remote API access.
     */
    public final Api getApi() {
        return new Api(this);
    }

    public boolean isAnonymized() {
        return ContentFilters.get().isEnabled();
    }

    @RequirePOST
    public void doDeleteBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        JSONObject json = req.getSubmittedForm();
        if (!json.has("bundles")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        Set<String> bundlesToDelete = getSelectedBundles(req, json);
        File rootDirectory = SupportPlugin.getRootDirectory();
        for(String bundleToDelete : bundlesToDelete) {
            File fileToDelete = new File(rootDirectory, bundleToDelete);
            logger.fine("Trying to delete bundle file "+ fileToDelete.getAbsolutePath());
            try {
                if (fileToDelete.delete()) {
                    logger.info("Bundle " + fileToDelete.getAbsolutePath() + " successfully deleted.");
                } else {
                    logger.log(Level.SEVERE, "Unable to delete file " + fileToDelete.getAbsolutePath());
                }
            } catch (RuntimeException e) {
                    logger.log(Level.SEVERE, "Unable to delete file " + fileToDelete.getAbsolutePath(), e);
            }
        }
        rsp.sendRedirect("generatedSupportContent");
    }

    @RequirePOST
    public void doDownloadBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        JSONObject json = req.getSubmittedForm();
        if (!json.has("bundles")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Set<String> bundlesToDownload = getSelectedBundles(req, json);
        File fileToDownload = null;
        if (bundlesToDownload.size() > 1) {
            // more than one bundles were selected, create a zip file
            fileToDownload = createZipFile(bundlesToDownload);
        } else {
            fileToDownload = new File(SupportPlugin.getRootDirectory(), bundlesToDownload.iterator().next());
        }
        logger.fine("Trying to download file "+ fileToDownload.getAbsolutePath());
        try {
            rsp.setContentType("application/zip");
            rsp.addHeader("Content-Disposition", "inline; filename=" + fileToDownload.getName() + ";");
            FileUtils.copyFile(fileToDownload, rsp.getOutputStream());
            logger.info("Bundle " + fileToDownload.getAbsolutePath() + " successfully downloaded");
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Unable to download file " + fileToDownload.getAbsolutePath(), e);
        } finally {
            if (bundlesToDownload.size() > 1) {
                if (fileToDownload.delete()) {
                    logger.log(Level.FINE, "Temporary multiBundle file deleted: " + fileToDownload.getAbsolutePath());
                } else {
                    logger.log(Level.SEVERE, "Unable to delete temporary multiBundle file: " + fileToDownload.getAbsolutePath());
                }
            }
        }
    }

    private Set<String> getSelectedBundles(StaplerRequest req, JSONObject json) throws ServletException, IOException {
        Set<String> bundles = new HashSet<>();
        List<String> existingBundles = getBundles();
        for (Selection s : req.bindJSONToList(Selection.class, json.get("bundles"))) {
            if (s.isSelected()) {
                if (existingBundles.contains(s.getName())) {
                    bundles.add(s.getName());
                } else {
                    logger.log(Level.FINE, "The bundle selected {0} does not exist, so it will not be processed", s.getName());
                }
            }
        }
        return bundles;
    }

    private File createZipFile(Set<String> bundles) throws IOException {
        File rootDirectory = SupportPlugin.getRootDirectory();
        File zipFile = File.createTempFile(
            String.format("multiBundle(%s)-", bundles.size()), ".zip");
        zipFile.deleteOnExit();
        try(FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos)) {
            byte[] buffer = new byte[1024]; 
            for (String bundle: bundles) {
                File file = new File(rootDirectory, bundle);
                try(FileInputStream fis = new FileInputStream(file)) {
                    zos.putNextEntry(new ZipEntry(file.getName()));
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error creating zip file: " + zipFile.getAbsolutePath(), e);
        }
        return zipFile;
    }

    /**
     * Generates a support bundle with selected components from the UI.
     * @param req The stapler request
     * @param rsp The stapler response
     * @throws ServletException If an error occurred during form submission
     * @throws IOException If an input or output exception occurs
     */
    @RequirePOST
    public void doDownload(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        doGenerateAllBundles(req, rsp);
    }

    /**
     * Generates a support bundle with selected components from the UI.
     * @param req The stapler request
     * @param rsp The stapler response
     * @throws ServletException If an error occurred during form submission
     * @throws IOException If an input or output exception occurs
     */
    @RequirePOST
    public void doGenerateAllBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        JSONObject json = req.getSubmittedForm();
        if (!json.has("components")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        logger.fine("Parsing request...");
        Set<String> remove = new HashSet<>();
        for (Selection s : req.bindJSONToList(Selection.class, json.get("components"))) {
            if (!s.isSelected()) {
                logger.log(Level.FINER, "Excluding ''{0}'' from list of components to include", s.getName());
                remove.add(s.getName());
                // JENKINS-63722: If "Master" or "Agents" are unselected, show a warning and add the new names for 
                // those components to the list of unselected components for backward compatibility
                if("Master".equals(s.getName()) || "Agents".equals(s.getName())) {
                    logger.log(Level.WARNING, Messages._SupportCommand_jenkins_63722_deprecated_ids(s.getName()).toString());
                    remove.add(s.getName()+ "JVMProcessSystemMetricsContents");
                    remove.add(s.getName()+ "SystemConfiguration");
                }
            }
        }
        logger.fine("Selecting components...");
        final List<Component> components = new ArrayList<>(getComponents());
        components.removeIf(c -> remove.contains(c.getId()) || !c.isEnabled());
        final SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            supportPlugin.setExcludedComponents(remove);
        }
        prepareBundle(rsp, components);
    }

    /**
     * Generates a support bundle with only requested components.
     * @param components component names separated by comma.
     * @param rsp The stapler response
     * @throws IOException If an input or output exception occurs
     */
    @RequirePOST
    public void doGenerateBundle(@QueryParameter("components") String components, StaplerResponse rsp) throws IOException {
        if (components == null) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "components parameter is mandatory");
            return;
        }
        Set<String> componentNames = Arrays
            .stream(components.split(","))
            .collect(Collectors.toSet());
        
        // JENKINS-63722: If "Master" or "Agents" are used, show a warning and add the new names for those components
        // to the selection for backward compatibility
        if(componentNames.contains("Master")) {
            logger.log(Level.WARNING, Messages._SupportCommand_jenkins_63722_deprecated_ids("Master").toString());
            componentNames.add("MasterJVMProcessSystemMetricsContents");
            componentNames.add("MasterSystemConfiguration");
        }
        if(componentNames.contains("Agents")) {
            logger.log(Level.WARNING, Messages._SupportCommand_jenkins_63722_deprecated_ids("Agents").toString());
            componentNames.add("AgentsJVMProcessSystemMetricsContents");
            componentNames.add("AgentsSystemConfiguration");
        }
        
        logger.fine("Selecting components...");
        List<Component> selectedComponents = getComponents().stream().filter(c -> componentNames.contains(c.getId())).collect(Collectors.toList());
        if (selectedComponents.isEmpty()) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST, "selected component list is empty");
            return;
        }
        prepareBundle(rsp, selectedComponents);
     }

    private void prepareBundle(StaplerResponse rsp, List<Component> components) throws IOException {
        logger.fine("Preparing response...");
        rsp.setContentType("application/zip");
        rsp.addHeader("Content-Disposition", "inline; filename=" + BundleFileName.generate() + ";");
        final ServletOutputStream servletOutputStream = rsp.getOutputStream();
        try {
            SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication2());
            try {
                try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                     SupportPlugin.writeBundle(servletOutputStream, components);
                } catch (IOException e) {
                    logger.log(Level.FINE, e.getMessage(), e);
                }
            } finally {
                SupportPlugin.clearRequesterAuthentication();
            }
        } finally {
            logger.fine("Response completed");
        }
    }

    public boolean selectedByDefault(Component c) {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        return c.isSelectedByDefault() && (supportPlugin == null || !supportPlugin.getExcludedComponents().contains(c.getId()));
    }

    @SuppressWarnings("unused") // used by Jelly
    public static boolean isManageBundlesSupported() {
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    public static class Selection {
        /** @see Component#getId */
        private final String name;qq
        private final boolean selected;

        @DataBoundConstructor
        public Selection(String name, boolean selected) {
            this.name = name;
            this.selected = selected;
        }

        public String getName() {
            return name;
        }

        public boolean isSelected() {
            return selected;
        }
    }
}
