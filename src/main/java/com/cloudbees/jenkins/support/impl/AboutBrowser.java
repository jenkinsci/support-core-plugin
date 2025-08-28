package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.security.Permission;
import hudson.util.Area;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;
import net.sf.uadetector.OperatingSystem;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Basic information about the user's browser.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutBrowser extends Component {
    @Override
    @NonNull
    public String getDisplayName() {
        return "About browser";
    }

    @Override
    public int getHash() {
        return 0;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @Override
    public boolean canBeGeneratedAsync() {
        return false;
    }

    @Override
    public void addContents(@NonNull Container result) {
        final StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
        if (currentRequest != null) {
            result.add(new PrintedContent("browser.md") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    out.println("Browser");
                    out.println("=======");
                    out.println();

                    Area screenResolution = Functions.getScreenResolution();
                    if (screenResolution != null) {
                        out.println("  * Screen size: " + screenResolution.toString());
                    }
                    UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
                    String userAgent = currentRequest.getHeader("User-Agent");
                    ReadableUserAgent agent = parser.parse(userAgent);
                    OperatingSystem operatingSystem = agent.getOperatingSystem();
                    out.println("  * User Agent");
                    out.println("      - Type:     " + agent.getType().getName());
                    out.println("      - Name:     " + agent.getName());
                    out.println("      - Family:   " + agent.getFamily());
                    out.println("      - Producer: " + agent.getProducer());
                    out.println("      - Version:  " + agent.getVersionNumber().toVersionString());
                    out.println("      - Raw:      `" + userAgent.replaceAll("`", "&#96;") + '`');
                    out.println("  * Operating System");
                    out.println("      - Name:     " + operatingSystem.getName());
                    out.println("      - Family:   " + operatingSystem.getFamily());
                    out.println("      - Producer: " + operatingSystem.getProducer());
                    out.println("      - Version:  "
                            + operatingSystem.getVersionNumber().toVersionString());
                    out.println();
                }

                @Override
                public boolean shouldBeFiltered() {
                    // The information of this content is not sensible, so it doesn't need to be filtered.
                    return false;
                }
            });
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.MISC;
    }
}
