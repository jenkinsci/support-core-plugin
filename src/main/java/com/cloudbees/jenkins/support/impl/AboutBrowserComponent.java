package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.model.AboutBrowser;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.security.Permission;
import hudson.util.Area;
import net.sf.uadetector.OperatingSystem;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.util.Collections;
import java.util.Set;

/**
 * Basic information about the user's browser.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutBrowserComponent extends Component {
    @Override
    @NonNull
    public String getDisplayName() {
        return "About browser";
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @Override
    public void addContents(@NonNull Container result) {
        final StaplerRequest currentRequest = Stapler.getCurrentRequest();
        if (currentRequest != null) {
            final AboutBrowser browser = new AboutBrowser();

            browser.setScreenSize(getScreenResolution());

            UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
            String userAgent = currentRequest.getHeader("User-Agent");
            ReadableUserAgent agent = parser.parse(userAgent);

            browser.setUserAgent(getUserAgent(agent, userAgent));

            browser.setOperatingSystem(getOperatingSystem(agent.getOperatingSystem()));

            result.add(new YamlContent("browser.yaml", browser));
            result.add(new MarkdownContent("browser.md", browser));
        }
    }

    private AboutBrowser.OperatingSystem getOperatingSystem(OperatingSystem operatingSystem) {
        AboutBrowser.OperatingSystem os = new AboutBrowser.OperatingSystem();
        os.setName(operatingSystem.getName());

        os.setFamily(operatingSystem.getFamily().toString());
        os.setProducer(operatingSystem.getProducer());
        os.setVersion(operatingSystem.getVersionNumber().toVersionString());

        return os;
    }

    private AboutBrowser.UserAgent getUserAgent(ReadableUserAgent agent, String userAgent) {
        AboutBrowser.UserAgent ua = new AboutBrowser.UserAgent();
        ua.setType(agent.getType().getName());
        ua.setName(agent.getName());

        ua.setFamily(agent.getFamily().toString());
        ua.setProducer(agent.getProducer());
        ua.setVersion(agent.getVersionNumber().toVersionString());
        ua.setRaw(userAgent.replaceAll("`", "&#96;"));
        return ua;
    }

    private String getScreenResolution() {
        Area screenResolution = Functions.getScreenResolution();
        if (screenResolution != null) {
            return screenResolution.toString();
        }

        return null;
    }
}
