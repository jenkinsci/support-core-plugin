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

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Collections;
import java.util.Set;

/**
 * @author schristou88
 */
@Extension
public class RootCAs extends Component {

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
    if (c instanceof SlaveComputer && !Boolean.TRUE.equals(((SlaveComputer) c).isUnix())) {
      return;
    }
    if (!node.createLauncher(TaskListener.NULL).isUnix()) {
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
                } finally {
                  out.flush();
                }
              }
            }
    );
  }

  public Future<String> getRootCA(Node node) throws IOException {
    VirtualChannel channel = node.getChannel();
    if (channel == null) {
      return null;
    }
    return channel.callAsync(new GetRootCA());
  }


  private static final class GetRootCA implements Callable<String, RuntimeException> {
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING"},
            justification = "Best effort"
    )
    public String call() {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        getRootCAList(bos);
        return bos.toString("utf-8");
      } catch (UnsupportedEncodingException e) {
        return bos.toString();
      }
    }

    private static final long serialVersionUID = 1L;
  }

  public static void getRootCAList(OutputStream out) throws UnsupportedEncodingException {
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "utf-8"), true);
    try {
      KeyStore instance = KeyStore.getInstance(KeyStore.getDefaultType());
      while (instance.aliases().hasMoreElements()) {
        String s = instance.aliases().nextElement();
        writer.println("========");
        writer.println("Alias: " + s);
        writer.println(instance.getCertificate(s).getPublicKey());
        writer.println("Trusted certificate: " + instance.isCertificateEntry(s));
      }
    } catch (KeyStoreException e) {
      e.printStackTrace(writer);
    }
  }
}