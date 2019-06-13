/*
 * The MIT License
 * 
 * Copyright (c) 2014 schristou88
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Extension
public class RootCAs extends Component {

  private final WeakHashMap<Node, String> certCache = new WeakHashMap<>();

  @Override
  public boolean isSelectedByDefault() {
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
    return "Root CAs";
  }

  @Override
  public void addContents(@NonNull Container container) {
    Jenkins j = Jenkins.getInstance();
    addContents(container, j);
    for (Node node : j.getNodes()) {
      addContents(container, node);
    }
  }

  private void addContents(@NonNull Container container, final @NonNull Node node) {
    Computer c = node.toComputer();
    if (c == null) {
      return;
    }
    String name;
    if (node instanceof Jenkins) {
      name = "master";
    } else {
      name = "slave/" + node.getNodeName();
    }
    container.add(
            new PrefilteredPrintedContent("nodes/{0}/RootCA.txt", name) {
              @Override
              public void printTo(PrintWriter out, ContentFilter filter) {
                try {
                  out.println(getRootCA(node, filter));
                } catch (IOException e) {
                  Functions.printStackTrace(e, out);
                } finally {
                  out.flush();
                }
              }
            }
    );
  }


  public String getRootCA(Node node) throws IOException {
    return AsyncResultCache.get(node, certCache, new GetRootCA(null), "Root CA info",
            "N/A: Either no connection to node, or no cached result");
  }

  public String getRootCA(Node node, ContentFilter filter) throws IOException {
    return AsyncResultCache.get(node, certCache, new GetRootCA(filter), "Root CA info",
            "N/A: Either no connection to node, or no cached result");
  }

  private static final class GetRootCA extends MasterToSlaveCallable<String, RuntimeException> {
    ContentFilter filter;

    private GetRootCA(ContentFilter filter) {
      this.filter = filter;
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING"},
            justification = "Best effort"
    )
    public String call() {
      StringWriter writer = new StringWriter();
      getRootCAList(writer, filter);
      return writer.toString();
    }

    private static final long serialVersionUID = 1L;
  }

  public static void getRootCAList(StringWriter writer, ContentFilter filter) {
    try {
      // Inspired by:
      // https://github.com/jenkinsci/jenkins-scripts/pull/82/files
      // https://stackoverflow.com/questions/8884831/listing-certificates-in-jvm-trust-store
      final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore) null);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
      for (int i = 0; i < trustManagers.length; i++) {
        writer.append("===== Trust Manager ").append(String.valueOf(i)).append(" =====\n");
        TrustManager trustManager = trustManagers[i];
        if (trustManager instanceof X509TrustManager) {
          final X509Certificate[] acceptedIssuers = ((X509TrustManager) trustManager).getAcceptedIssuers();
          writer.append("It is an X.509 Trust Manager containing ")
                  .append(String.valueOf(acceptedIssuers.length))
                  .append(" certificates:\n");
          for (X509Certificate x509Certificate : acceptedIssuers) {
            if(filter != null) {
              writer.append(filter.filter(x509Certificate.getSubjectX500Principal().toString())).append('\n');
            } else {
              writer.append(x509Certificate.getSubjectX500Principal().toString()).append('\n');
            }
          }
        } else {
          writer.append("Skipping as it is not an X.509 Trust Manager.\n");
          writer.append("Class Name: ").append(trustManager.getClass().getName()).append('\n');
        }
      }
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      writer.write(Functions.printThrowable(e));
    }
  }

  public static void getRootCAList(StringWriter writer) {
    getRootCAList(writer, null);
  }
}
