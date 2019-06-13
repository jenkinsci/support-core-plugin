package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;

/**
 * Basic information about the user's authentication.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutUser extends Component {
    @Override
    @NonNull
    public String getDisplayName() {
        return "About user (basic authentication details only)";
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @Override
    public void addContents(@NonNull Container result) {
        final Authentication authentication = SupportPlugin.getRequesterAuthentication();
        if (authentication != null) {
            result.add(new PrefilteredPrintedContent("user.md") {
                @Override
                protected void printTo(PrintWriter out, ContentFilter filter) throws IOException {
                    out.println("User");
                    out.println("====");
                    out.println();
                    out.println("Authentication");
                    out.println("--------------");
                    out.println();
                    out.println("  * Authenticated: " + authentication.isAuthenticated());
                    out.println("  * Name: " + (filter != null ? filter.filter(authentication.getName()) : authentication.getName()));
                    GrantedAuthority[] authorities = authentication.getAuthorities();
                    if (authorities != null) {
                        out.println("  * Authorities ");
                        for (GrantedAuthority authority : authorities) {
                            out.println(
                                    "      - " + (authority == null ? "null" : "`" + authority.toString().replaceAll(
                                            "`", "&#96;") + "`"));
                        }
                    }
                    out.println("  * Raw: `" + authentication.toString().replaceAll("`", "&#96;") + "`");
                    out.println();
                }
            });
        }
    }
}
