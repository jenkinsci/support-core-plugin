package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.model.AboutUser;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;

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
            AboutUser user = new AboutUser();
            user.setAuthenticated(authentication.isAuthenticated());
            user.setName(authentication.getName());

            user.setAuthorities(getAuthorities(authentication));

            user.setRaw(authentication.toString());

            result.add(new MarkdownContent("user.md", user));
            result.add(new YamlContent("user.yaml", user));
        }
    }

    private List<String> getAuthorities(Authentication authentication) {
        List<String> authoritiesList = new ArrayList<String>();
        GrantedAuthority[] authorities = authentication.getAuthorities();
        if (authorities != null) {
            for (GrantedAuthority authority : authorities) {
                authoritiesList.add(authority.getAuthority());
            }
        }
        
        return authoritiesList;
    }
}
