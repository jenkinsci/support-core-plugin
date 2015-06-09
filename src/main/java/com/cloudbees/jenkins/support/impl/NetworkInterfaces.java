/*
 * The MIT License
 * 
 * Copyright (c) 2015 schristou88
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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.Util;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.security.Permission;
import hudson.util.HexBinaryConverter;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Hex;
import org.codehaus.groovy.runtime.StackTraceUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author schristou88
 */
@Extension
public class NetworkInterfaces extends Component {
    private final WeakHashMap<Node, String> networkInterfaceCache = new WeakHashMap<Node, String>();

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return "Networking Interface";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(
                new Content("nodes/master/networkInterface.md") {
                    @Override
                    public void writeTo(OutputStream os) throws IOException {
                        os.write(getNetworkInterface(Jenkins.getInstance()).getBytes("UTF-8"));
                    }
                }
        );

        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(
                    new Content("nodes/slave/" + node.getNodeName() + "/networkInterface.md") {
                        @Override
                        public void writeTo(OutputStream os) throws IOException {
                            os.write(getNetworkInterface(node).getBytes("UTF-8"));
                        }
                    }
            );
        }
    }

    public String getNetworkInterface(Node node) throws IOException {
        return AsyncResultCache.get(node,
                networkInterfaceCache,
                new GetNetworkInterfaces(),
                "network interfaces",
                "N/A: No connection to node, or no cache.");
    }

    private static final class GetNetworkInterfaces implements Callable<String, RuntimeException> {
        public String call() {
            StringBuilder bos = new StringBuilder();

            try {
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    bos.append("-----------\n");

                    NetworkInterface ni = networkInterfaces.nextElement();
                    bos.append(" * Name ").append(ni.getDisplayName()).append("\n");

                    byte[] hardwareAddress = ni.getHardwareAddress();

                    // Do not have permissions or address does not exist
                    if (hardwareAddress != null && hardwareAddress.length != 0)
                        bos.append(" ** Hardware Address - ").append(Util.toHexString(hardwareAddress)).append("\n");

                    bos.append(" ** Index - ").append(ni.getIndex()).append("\n");
                    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress =  inetAddresses.nextElement();
                        bos.append(" ** InetAddress - ").append(inetAddress).append("\n");
                    }
                    bos.append(" ** MTU - ").append(ni.getMTU()).append("\n");
                    bos.append(" ** Is Up - ").append(ni.isUp()).append("\n");
                    bos.append(" ** Is Virtual - ").append(ni.isVirtual()).append("\n");
                    bos.append(" ** Is Loopback - ").append(ni.isLoopback()).append("\n");
                    bos.append(" ** Is Point to Point - ").append(ni.isPointToPoint()).append("\n");
                    bos.append(" ** Supports multicast - ").append(ni.supportsMulticast()).append("\n");

                    if (ni.getParent() != null) {
                        bos.append(" ** Child of - ").append(ni.getParent().getDisplayName()).append("\n");
                    }
                }
            } catch (SocketException e) {
                bos.append(e.getMessage());
            }

            return bos.toString();
        }
    }
}
