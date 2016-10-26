/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

import com.cloudbees.jenkins.support.util.Helper;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main root action for generating support.
 */
@Extension
public class SupportAction implements RootAction {

    public static final Permission CREATE_BUNDLE = SupportPlugin.CREATE_BUNDLE;
    private static final String  BUNDLE_COLLECTION_NAME = "BundleCollection";
    /**
     * Our logger (retain an instance ref to avoid classloader leaks).
     */
    private final Logger logger = Logger.getLogger(SupportAction.class.getName());

    public String getIconFileName() {
        return "/plugin/support-core/images/24x24/support.png";
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

    @SuppressWarnings("unused") // used by Stapler
    public List<Component> getComponents() {
        return SupportPlugin.getComponents();
    }

    @RequirePOST
    public void doDownload(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        final Jenkins instance = Helper.getActiveInstance();
        instance.getAuthorizationStrategy().getACL(instance).checkPermission(CREATE_BUNDLE);

        JSONObject json = req.getSubmittedForm();
        if (!json.has("components")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        logger.fine("Parsing request...");
        Set<String> remove = new HashSet<String>();
        for (Selection s : req.bindJSONToList(Selection.class, json.get("components"))) {
            if (!s.isSelected()) {
                logger.log(Level.FINER, "Excluding ''{0}'' from list of components to include", s.getName());
                remove.add(s.getName());
            }
        }
        logger.fine("Selecting components...");
        final List<Component> components = new ArrayList<Component>(getComponents());
        for (Iterator<Component> iterator = components.iterator(); iterator.hasNext(); ) {
            Component c = iterator.next();
            if (remove.contains(c.getId()) || !c.isEnabled()) {
                iterator.remove();
            }
        }
        final SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            supportPlugin.setExcludedComponents(remove);
        }
        logger.fine("Preparing response...");
        rsp.setContentType("application/zip");

        String filename = "support"; // default bundle filename
        if (supportPlugin != null) {
            SupportProvider supportProvider = supportPlugin.getSupportProvider();
            if (supportProvider != null) {
                // let the provider name it
                filename = supportProvider.getName();
            }
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        rsp.addHeader("Content-Disposition", "inline; filename=" + filename + "_" + dateFormat.format(new Date()) + ".zip;");
        final ServletOutputStream servletOutputStream = rsp.getOutputStream();
        try {
            SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication());
            try {
                SecurityContext old = ACL.impersonate(ACL.SYSTEM);
                try {
                    SupportPlugin.writeBundle(servletOutputStream, components);
                } catch (IOException e) {
                    logger.log(Level.FINE, e.getMessage(), e);
                } finally {
                    SecurityContextHolder.setContext(old);
                }
            } finally {
                SupportPlugin.clearRequesterAuthentication();
            }
        } finally {
            logger.fine("Response completed");
        }
    }

    @Restricted(NoExternalUse.class)
    public void doDownloadOrDelete(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        final Jenkins instance = Helper.getActiveInstance();
        instance.getAuthorizationStrategy().getACL(instance).checkPermission(CREATE_BUNDLE);

        JSONObject json = req.getSubmittedForm();
        String goal = json.get("goalType").toString();

        if("download".equals(goal)){
            doDownloadBundles(req,rsp);
        }
        else{
            doDeleteBundles(req,rsp);
        }
    }

    @Restricted(NoExternalUse.class)
    public void doDownloadBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        if (!"POST".equals(req.getMethod())) {
            rsp.sendRedirect2(".");
            return;
        }
        JSONObject json = req.getSubmittedForm();
        String goal = json.get("goalType").toString();

        if (!json.has("components")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        List<Integer> indexList = getSelectedIndices(req);
        rsp.setContentType("application/zip");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        rsp.addHeader("Content-Disposition", "inline; filename="+BUNDLE_COLLECTION_NAME+"_"+dateFormat.format(new Date())+".zip;");
        final ServletOutputStream servletOutputStream = rsp.getOutputStream();
        BundleBrowser bundleBrowser = BundleBrowser.getBundleBrowser();
        try {
            SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication());
            try {
                SecurityContext old = ACL.impersonate(ACL.SYSTEM);
                try {
                    SupportPlugin.writeBundleCollection(servletOutputStream,bundleBrowser.getSelectedFiles(indexList));
                } catch (IOException e) {
                    logger.log(Level.FINE, e.getMessage(), e);
                } finally {
                    SecurityContextHolder.setContext(old);
                }
            } finally {
                SupportPlugin.clearRequesterAuthentication();
            }
        } finally {
            logger.fine("Response completed");
        }
    }

    @Restricted(NoExternalUse.class)
    public void doDeleteBundles(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        if (!"POST".equals(req.getMethod())) {
            rsp.sendRedirect2(".");
            return;
        }
        JSONObject json = req.getSubmittedForm();
        if (!json.has("components")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        List<Integer> indexList = getSelectedIndices(req);
        BundleBrowser bundleBrowser = BundleBrowser.getBundleBrowser();
        bundleBrowser.deleteBundle(indexList);
        PrintWriter out = rsp.getWriter();
        out.println("</script>");
        rsp.forwardToPreviousPage(req);

        rsp.setContentType("text/html");
        out.println("<script type=\"text/javascript\">");
        out.println("alert('Bundle(s) Deleted!');");
        try {
            SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication());
            SecurityContext old = ACL.impersonate(ACL.SYSTEM);
            SecurityContextHolder.setContext(old);
            SupportPlugin.clearRequesterAuthentication();
        } finally {
            logger.fine("Response completed");
        }
    }

    @Restricted(NoExternalUse.class)
    public List<File> getList() throws IOException {
        BundleBrowser bundleBrowser = BundleBrowser.getBundleBrowser();
        return bundleBrowser.getZipFileList();
    }

    public boolean selectedByDefault(Component c) {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        return c.isSelectedByDefault() && (supportPlugin == null || !supportPlugin.getExcludedComponents().contains(c.getId()));
    }

    private List<Integer> getSelectedIndices(StaplerRequest req) throws ServletException {
        ArrayList<Integer> selectedFileIndices = new ArrayList<Integer>();
        JSONObject json = req.getSubmittedForm();
        try{
            req.getSubmittedForm().getJSONObject("components");
            selectedFileIndices.add(0);
        }catch (Exception e) {
            JSONArray jsonArray = json.getJSONArray("components");

            for (int i = 0; i < jsonArray.size(); i++) {
                String isSelected = jsonArray.getJSONObject(i).get("selected").toString();
                if ("true".equals(isSelected)) {
                    selectedFileIndices.add(i);
                }
            }
        }
        return selectedFileIndices;
    }

    public static class Selection {
        /** @see Component#getId */
        private final String name;
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
