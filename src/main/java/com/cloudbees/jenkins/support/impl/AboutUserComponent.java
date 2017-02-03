package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.StringContent;
import com.cloudbees.jenkins.support.model.AboutUser;
import com.cloudbees.jenkins.support.util.SupportUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Basic information about the user's authentication.
 *
 * @author Stephen Connolly
 */
@Extension
public class AboutUserComponent extends Component {
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
            result.add(new PrintedContent("user.yaml") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    AboutUser user = new AboutUser();
                    user.setAuthenticated(authentication.isAuthenticated());
                    user.setName(authentication.getName());

                    user.setAuthorities(getAuthorities(authentication));

                    user.setRaw(authentication.toString());
                    out.println(SupportUtils.toString(user));
                }
            });
        }
    }

    private List<GrantedAuthority> getAuthorities(Authentication authentication) {
        List<GrantedAuthority> authoritiesList = new ArrayList<GrantedAuthority>();
        GrantedAuthority[] authorities = authentication.getAuthorities();
        if (authorities != null) {
            Collections.addAll(authoritiesList, authorities);
        }
        return authoritiesList;
    }
}
