package com.cloudbees.jenkins.support.model;

import lombok.Data;
import org.acegisecurity.GrantedAuthority;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */
@Data
public class AboutUser implements Serializable, MarkdownFile {
    boolean isAuthenticated;
    String name;
    List<GrantedAuthority> authorities;
    String raw;

    public void addAuthority(GrantedAuthority authority) {
        authorities.add(authority);
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
            for (GrantedAuthority authority : authorities) {
                out.println("      - " + (authority == null ? "null" : "`" + authority.toString().replaceAll("`", "&#96;") + "`"));
            }
        }

        out.println("  * Raw: `" + raw.replaceAll("`", "&#96;") + "`");
        out.println();
    }
}
