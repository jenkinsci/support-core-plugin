package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Set;

/**
 * Attempts to detect reverse proxies in front of Jenkins.
 */
@Extension
public class ReverseProxy extends Component {

  public enum Trilean {
    TRUE, FALSE, UNKNOWN
  }

  static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

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
      @Override protected void printTo(PrintWriter out) {
        out.println("Reverse Proxy");
        out.println("=============");
        out.println(String.format(" * Detected `%s` header: %s", X_FORWARDED_FOR_HEADER, isXForwardForHeaderDetected()));
      }
    });
  }

  private Trilean isXForwardForHeaderDetected() {
    StaplerRequest req = getCurrentRequest();
    if (req == null) {
      return Trilean.UNKNOWN;
    }
    return req.getHeader(X_FORWARDED_FOR_HEADER) != null ? Trilean.TRUE : Trilean.FALSE;
  }

  protected StaplerRequest getCurrentRequest() {
    return Stapler.getCurrentRequest();
  }
}
