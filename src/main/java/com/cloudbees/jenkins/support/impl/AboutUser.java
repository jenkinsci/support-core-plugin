package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.ACL;
import hudson.security.Permission;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

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
    public int getHash() { return 2; }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @Override
    public void addContents(@NonNull Container result) {
        final Authentication authentication = Jenkins.getAuthentication2();
        if (!authentication.equals(ACL.SYSTEM2)) {
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
                    out.println("  * Name: " + ContentFilter.filter(filter, authentication.getName()));
                    Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
                    if (authorities != null) {
                        out.println("  * Authorities ");
                        for (GrantedAuthority authority : authorities) {
                            out.println("      - "
                                    + (authority == null
                                            ? "null"
                                            : "`" + authority.toString().replaceAll("`", "&#96;") + "`"));
                        }
                    }
                    out.println();
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
