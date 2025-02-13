package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Attempts to detect reverse proxies in front of Jenkins.
 */
@Extension
public class ReverseProxy extends Component {

    public enum Trilean {
        TRUE,
        FALSE,
        UNKNOWN
    }

    // [RFC 7239, section 4: Forwarded](https://tools.ietf.org/html/rfc7239#section-4) standard header.
    private static final String FORWARDED_HEADER = "Forwarded";
    // Non-standard headers.
    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
    private static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";
    private static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

    static final Collection<String> FORWARDED_HEADERS = ImmutableList.of(
            FORWARDED_HEADER,
            X_FORWARDED_FOR_HEADER,
            X_FORWARDED_PROTO_HEADER,
            X_FORWARDED_HOST_HEADER,
            X_FORWARDED_PORT_HEADER);

    @Override
    public boolean canBeGeneratedAsync(){
        return false;
    }

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Reverse Proxy";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrintedContent("reverse-proxy.md") {
            @Override
            protected void printTo(PrintWriter out) {
                out.println("Reverse Proxy");
                out.println("=============");
                StaplerRequest2 currentRequest = getCurrentRequest();
                for (String forwardedHeader : FORWARDED_HEADERS) {
                    out.println(String.format(
                            " * Detected `%s` header: %s",
                            forwardedHeader, isForwardedHeaderDetected(currentRequest, forwardedHeader)));
                }
            }

            @Override
            public boolean shouldBeFiltered() {
                // The information of this content is not sensible, so it doesn't need to be filtered.
                return false;
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }

    private Trilean isForwardedHeaderDetected(StaplerRequest2 req, String header) {
        if (req == null) {
            return Trilean.UNKNOWN;
        }
        return req.getHeader(header) != null ? Trilean.TRUE : Trilean.FALSE;
    }

    protected StaplerRequest2 getCurrentRequest() {
        return Stapler.getCurrentRequest2();
    }
}