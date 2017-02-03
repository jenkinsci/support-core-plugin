package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.model.AboutBrowser;
import com.cloudbees.jenkins.support.util.SupportUtils;
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

import java.io.IOException;
import java.io.PrintWriter;
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
            result.add(new PrintedContent("browser.yaml") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    AboutBrowser browser = new AboutBrowser();

                    Area screenResolution = Functions.getScreenResolution();
                    if (screenResolution != null) {
                        browser.setScreenSize(screenResolution.toString());
                    }
                    UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
                    String userAgent = currentRequest.getHeader("User-Agent");
                    ReadableUserAgent agent = parser.parse(userAgent);
                    OperatingSystem operatingSystem = agent.getOperatingSystem();

                    AboutBrowser.UserAgent ua = new AboutBrowser.UserAgent();
                    ua.setType(agent.getType().getName());
                    ua.setName(agent.getName());

                    ua.setFamily(agent.getFamily().toString());
                    ua.setProducer(agent.getProducer());
                    ua.setVersion(agent.getVersionNumber().toVersionString());
                    ua.setRaw(userAgent.replaceAll("`", "&#96;"));

                    browser.setUserAgent(ua);

                    AboutBrowser.OperatingSystem os = new AboutBrowser.OperatingSystem();
                    os.setName(operatingSystem.getName());

                    os.setFamily(operatingSystem.getFamily().toString());
                    os.setProducer(operatingSystem.getProducer());
                    os.setVersion(operatingSystem.getVersionNumber().toVersionString());
                    out.println(SupportUtils.toString(browser));

                }
            });
        }
    }
}
