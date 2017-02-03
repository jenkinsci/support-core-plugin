package com.cloudbees.jenkins.support.model;

import lombok.Data;
import org.acegisecurity.GrantedAuthority;

import java.io.Serializable;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */
@Data
public class AboutUser implements Serializable {
    boolean isAuthenticated;
    String name;
    List<GrantedAuthority> authorities;
    String raw;

    public void addAuthority(GrantedAuthority authority) {
        authorities.add(authority);
    }
}
