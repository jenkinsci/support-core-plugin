package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Attempts to detect reverse proxies in front of Jenkins.
 */
@Extension
public class ReverseProxy extends Component {

  private static final Logger LOG = Logger.getLogger(ReverseProxy.class.getName());

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
      @Override protected void printTo(PrintWriter out) throws IOException {
        out.println("Reverse Proxy");
        out.println("========");
        out.println(String.format(" * Detected `%s` header: %b", X_FORWARDED_FOR_HEADER, isXForwardForHeaderDetected()));
      }
    });
  }

  private boolean isXForwardForHeaderDetected() {
    try {
      String rootUrl = Jenkins.get().getRootUrl();
      if (rootUrl == null) {
        return false;
      }

      HttpURLConnection.setFollowRedirects(true);
      HttpURLConnection httpURLConnection = getHttpURLConnection(rootUrl);

      if (httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
        return false;
      }

      return httpURLConnection.getHeaderField(X_FORWARDED_FOR_HEADER) != null;
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, String.format("Failed to detect %s header", X_FORWARDED_FOR_HEADER), e);
      return false;
    }
  }

  protected HttpURLConnection getHttpURLConnection(String url) throws IOException {
    return (HttpURLConnection) new URL(url).openConnection();
  }
}
