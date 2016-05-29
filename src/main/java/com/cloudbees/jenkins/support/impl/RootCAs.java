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
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.util.Helper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.remoting.RoleChecker;

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author schristou88
 */
@Extension
public class RootCAs extends Component {

  private final WeakHashMap<Node, String> certCache = new WeakHashMap<Node, String>();

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
    Jenkins j = Helper.getActiveInstance();
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
            new Content("nodes/" + name + "/RootCA.txt") {
              @Override
              public void writeTo(OutputStream os) throws IOException {
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, "utf-8")));
                try {
                  out.println(getRootCA(node));
                } catch (IOException e) {
                  e.printStackTrace(out);
                } catch (InterruptedException e) {
                  e.printStackTrace(out);
                } finally {
                  out.flush();
                }
              }
            }
    );
  }

  public String getRootCA(Node node) throws IOException, InterruptedException {
    return AsyncResultCache.get(node, certCache, new GetRootCA(), "Root CA info",
            "N/A: Either no connection to node, or no cached result");
  }


  private static final class GetRootCA implements Callable<String, RuntimeException> {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING"},
            justification = "Best effort"
    )
    public String call() {
      StringWriter writer = new StringWriter();
      getRootCAList(writer);
      return writer.toString();
    }

    /** {@inheritDoc} */
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
      // TODO: do we have to verify some role?
    }

    private static final long serialVersionUID = 1L;
  }

  public static void getRootCAList(StringWriter writer) {
    KeyStore instance = null;
    try {
      instance = KeyStore.getInstance(KeyStore.getDefaultType());
      Enumeration<String> aliases = instance.aliases();
      while (aliases.hasMoreElements()) {
        String s = aliases.nextElement();
        writer.append("========");
        writer.append("Alias: " + s);
        writer.append(instance.getCertificate(s).getPublicKey().toString());
        writer.append("Trusted certificate: " + instance.isCertificateEntry(s));
      }
    } catch (KeyStoreException e) {
      writer.write(Functions.printThrowable(e));
    }
  }
}