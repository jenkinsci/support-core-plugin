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
import com.cloudbees.jenkins.support.api.UnfilteredStringContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.security.Permission;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Formatter;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;

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
        Jenkins j = Jenkins.get();
        addContents(container, j);
        for (Node node : j.getNodes()) {
            addContents(container, node);
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }

    private void addContents(@NonNull Container container, final @NonNull Node node) {
        Computer c = node.toComputer();
        if (c == null) {
            return;
        }

        String name;
        String[] params;
        if (node instanceof Jenkins) {
            name = "nodes/master/RootCA.txt";
            params = new String[0];
        } else {
            name = "nodes/slave/{0}/RootCA.txt";
            params = new String[] {node.getNodeName()};
        }

        try {
            container.add(new UnfilteredStringContent(name, params, getRootCA(node)));
        } catch (IOException e) {
            container.add(new UnfilteredStringContent(name, params, Functions.printThrowable(e)));
        }
    }

    public String getRootCA(Node node) throws IOException {
        return AsyncResultCache.get(
                node,
                certCache,
                new GetRootCA(),
                "Root CA info",
                "N/A: Either no connection to node, or no cached result");
    }

    private static final class GetRootCA extends MasterToSlaveCallable<String, RuntimeException> {

        public String call() {
            StringWriter writer = new StringWriter();
            getTrustStoreConfiguration(writer);
            getRootCAList(writer);
            return writer.toString();
        }

        private static final long serialVersionUID = 1L;
    }

    public static void getTrustStoreConfiguration(StringWriter writer) {
        // Customizing the Default Keystores and Truststores, Store Types, and Store Passwords
        // https://docs.oracle.com/en/java/javase/11/security/java-secure-socket-extension-jsse-reference-guide.html#GUID-7D9F43B8-AABF-4C5B-93E6-3AFB18B66150

        var javaHome = System.getProperty("java.home");
        Optional<String> maybeExplictTrustStorePath =
                Optional.ofNullable(System.getProperty("javax.net.ssl.trustStore"));
        var jssecacertPath = Path.of(javaHome, "lib", "security", "jssecacerts");
        var cacertPath = Path.of(javaHome, "lib", "security", "cacerts");

        var formatter = new Formatter(writer);
        writer.write("==== Trust Store Configuration ====\n");
        formatter.format("javax.net.ssl.trustStore: %s", maybeExplictTrustStorePath.orElse("not set"));
        if (maybeExplictTrustStorePath.isPresent()) {
            formatter.format(": %s%n", summarizePath(Path.of(maybeExplictTrustStorePath.get())));
        } else {
            writer.write('\n');
        }
        formatter.format("%s: %s%n", jssecacertPath, summarizePath(jssecacertPath));
        formatter.format("%s: %s%n", cacertPath, summarizePath(cacertPath));
        writer.write('\n');
    }

    public static void getRootCAList(StringWriter writer) {
        try {
            // Inspired by:
            // https://github.com/jenkinsci/jenkins-scripts/pull/82/files
            // https://stackoverflow.com/questions/8884831/listing-certificates-in-jvm-trust-store
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()); // FIPS OK: Reading certificates.
            trustManagerFactory.init((KeyStore) null); // FIPS OK: Not Used.
            /*
            FIPS OK: https://downloads.bouncycastle.org/fips-java/BC-FJA-UserGuide-1.0.2.pdf Section 7 Key Stores says:
            The FIPS keystore type will read both BCFKS files and JKS files with one caveat, it will not accept a JKS file containing a secret/private key. This can be useful where JKS files are being used solely for the storage of certificates, such as with the cacerts file found in the typical JVM.
            Meaning, reading certificates from a JKS file is fine.
            */
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
                        writer.append(x509Certificate.getSubjectX500Principal().toString())
                                .append('\n');
                    }
                } else {
                    writer.append("Skipping as it is not an X.509 Trust Manager.\n");
                    writer.append("Class Name: ")
                            .append(trustManager.getClass().getName())
                            .append('\n');
                }
            }
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            writer.write(Functions.printThrowable(e));
        }
    }

    static String summarizePath(Path path) {
        if (!Files.exists(path)) {
            return "not a file";
        }
        try {
            var fileAttributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (fileAttributes.isSymbolicLink()) {
                Path target = Files.readSymbolicLink(path);
                return "symlink to " + target.toAbsolutePath();
            } else if (fileAttributes.isRegularFile()) {
                return "regular file";
            } else if (fileAttributes.isDirectory()) {
                return "directory";
            } else {
                return "other";
            }
        } catch (IOException e) {
            return "error reading file attributes: " + e.getMessage();
        }
    }
}
