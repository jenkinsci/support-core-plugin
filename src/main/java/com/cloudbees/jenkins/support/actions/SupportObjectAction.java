package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Support Action at Object level.
 * 
 * @param <T> The type of {@link AbstractModelObject}
 */
public abstract class SupportObjectAction<T extends AbstractModelObject> implements Action {

    static final Logger LOGGER = Logger.getLogger(SupportObjectAction.class.getName());

    private List<? extends ObjectComponent<T>> components = new ArrayList<>();

    @Nonnull
    private final T object;

    public SupportObjectAction(@Nonnull T object) {
        this.object = object;
    }

    @Nonnull
    public final T getObject() {
        return object;
    }

    @Override
    public String getUrlName() {
        return "support";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/support-core/images/24x24/support.png";
    }

    @DataBoundSetter
    public void setComponents(List<? extends ObjectComponent<T>> components) {
        this.components = new ArrayList<>(components);
    }
    
    @SuppressWarnings("unused") // used by Stapler
    public Localizable getActionTitle() {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            SupportProvider supportProvider = supportPlugin.getSupportProvider();
            if (supportProvider != null) {
                return supportProvider.getActionTitle();
            }
        }
        return Messages._SupportObjectAction_DefaultActionTitle();
    }

    @SuppressWarnings("unused") // used by Stapler
    public Localizable getActionBlurb() {
        SupportPlugin supportPlugin = SupportPlugin.getInstance();
        if (supportPlugin != null) {
            SupportProvider supportProvider = supportPlugin.getSupportProvider();
            if (supportProvider != null) {
                return supportProvider.getActionBlurb();
            }
        }
        return Messages._SupportObjectAction_DefaultActionBlurb();
    }

    public List<? extends ObjectComponent<T>> getComponents() {
        return components;
    }

    @SuppressWarnings("unused") // used by Stapler
    public List<ObjectComponentDescriptor<T>> getApplicableComponentsDescriptors() {
        return ObjectComponent.for_(object);
    }

    @SuppressWarnings("unused") // used by Stapler
    public Map<ObjectComponentDescriptor<T>, ObjectComponent<T>> getDefaultComponentsDescriptors() {
        return ObjectComponent.allInstances(object).stream()
                .filter(oComponent -> oComponent.isSelectedByDefault(object))
                .collect(Collectors.toMap(
                        ObjectComponent::getDescriptor,
                        Function.identity()
                ));
    }
    
    @RequirePOST
    @SuppressWarnings("unused") // used by Stapler
    public final void doGenerateAndDownload(StaplerRequest req, StaplerResponse rsp) 
            throws ServletException, IOException, Descriptor.FormException {
        
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        LOGGER.fine("Preparing response...");
        rsp.setContentType("application/zip");

        JSONObject json = req.getSubmittedForm();
        if (!json.has("components")) {
            rsp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        LOGGER.fine("Parsing request...");
        List<ObjectComponent<T>> components = new ArrayList<>(parseRequest(req));

        rsp.addHeader("Content-Disposition", "inline; filename=" + SupportPlugin.getBundleFileName() + ";");
        
        try {
            SupportPlugin.setRequesterAuthentication(Jenkins.getAuthentication());
            try (ACLContext old = ACL.as(ACL.SYSTEM)) {
                SupportPlugin.writeBundle(rsp.getOutputStream(), components, new ComponentVisitor() {
                    @Override
                    public <C extends Component> void visit(Container container, C component) {
                        ((ObjectComponent<T>)component).addContents(container, object);
                    }
                });
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, e.getMessage(), e);
            } finally {
                SupportPlugin.clearRequesterAuthentication();
            }
        } finally {
            LOGGER.fine("Response completed");
        }
    }

    /**
     * Parse the stapler JSON output and retrieve configured components.
     * 
     * @param req the request
     * @return the {@link DescribableList} of components
     */
    protected final List<ObjectComponent<T>> parseRequest(StaplerRequest req)
            throws ServletException, Descriptor.FormException {

        LOGGER.fine("Parsing request...");
        // Inspired by https://github.com/jenkinsci/workflow-job-plugin/blob/workflow-job-2.35/src/main/java/org/jenkinsci/plugins/workflow/job/properties/PipelineTriggersJobProperty.java
        DescribableList<ObjectComponent<T>, ObjectComponentDescriptor<T>> components =
                new DescribableList<>(Saveable.NOOP);
        try {
            JSONObject componentsSection = new JSONObject();
            if (req.getSubmittedForm() != null) {
                componentsSection = req.getSubmittedForm().getJSONObject("components");
            }
            components.rebuild(req, componentsSection, getApplicableComponentsDescriptors());
        } catch (IOException e) {
            throw new Descriptor.FormException(e, "components");
        }
        return components;
    }
}
