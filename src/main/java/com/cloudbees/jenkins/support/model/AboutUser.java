package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */

public class AboutUser implements Serializable, MarkdownFile {
    boolean isAuthenticated;
    String name;
    List<String> authorities = new ArrayList<>();
    String raw;

    public void addAuthority(String authority) {
        authorities.add(authority);
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        isAuthenticated = authenticated;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(List<String> authorities) {
        this.authorities = authorities;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("User");
        out.println("====");
        out.println();
        out.println("Authentication");
        out.println("--------------");
        out.println();
        out.println("  * Authenticated: " + isAuthenticated);
        out.println("  * Name: " + name);

        if (authorities != null) {
            out.println("  * Authorities ");
            for (String authority : authorities) {
                out.println("      - " + (authority == null ? "null" : "`" + authority.replaceAll("`", "&#96;") + "`"));
            }
        }

        out.println("  * Raw: `" + raw.replaceAll("`", "&#96;") + "`");
        out.println();
    }
}
