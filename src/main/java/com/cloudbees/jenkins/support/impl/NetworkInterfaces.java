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
import com.cloudbees.jenkins.support.api.*;
import com.cloudbees.jenkins.support.model.Network;
import com.cloudbees.jenkins.support.util.Helper;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.remoting.RoleChecker;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author schristou88
 */
@Extension
public class NetworkInterfaces extends Component {
    private final Logger LOGGER = Logger.getLogger(NetworkInterfaces.class.getName());

    private final WeakHashMap<Node, Network> networkInterfaceCache = new WeakHashMap<Node, Network>();

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

        Network mni = getNetworkInterface(Jenkins.getInstance());
        result.add(new MarkdownContent("nodes/master/networkInterface.md", mni));
        result.add(new YamlContent("nodes/master/networkInterface.yaml", mni));

        for (final Node node : Helper.getActiveInstance().getNodes()) {
            Network sni = getNetworkInterface(node);
            result.add(new MarkdownContent("nodes/slave/" + node.getNodeName() + "/networkInterface.md", sni));
            result.add(new YamlContent("nodes/slave/" + node.getNodeName() + "/networkInterface.yaml", sni));
        }
    }

    /**
     *
     * @return Empty network configuration if there was an error going to the slave machine to get information.
     */
    private Network getNetworkInterface(Node node) {
        try {
            return AsyncResultCache.get(node,
                    networkInterfaceCache,
                    new GetNetworkInterfaces(),
                    "network interfaces", new Network());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Issue obtaining network interfaces from node: " + node.getDisplayName(), e);
            return new Network();
        }

    }

    private static final class GetNetworkInterfaces extends MasterToSlaveCallable<Network, RuntimeException> {
        public Network call() {
            Network network = new Network();
            try {
                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaces.hasMoreElements()) {
                    Network.NetworkInterface networkInterface = new Network.NetworkInterface();

                    NetworkInterface ni = networkInterfaces.nextElement();
                    networkInterface.setDisplayName(ni.getDisplayName());

                    byte[] hardwareAddress = ni.getHardwareAddress();

                    // Do not have permissions or address does not exist
                    if (hardwareAddress != null && hardwareAddress.length != 0)
                        networkInterface.setHardwareAddress(Util.toHexString(hardwareAddress));

                    networkInterface.setIndex(ni.getIndex());

                    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress inetAddress =  inetAddresses.nextElement();

                        Network.NetworkInterface.InetAddress address = new Network.NetworkInterface.InetAddress();
                        address.setInetAddress(inetAddress.toString());
                        networkInterface.addInetAddress(address);
                    }

                    networkInterface.setMtu(ni.getMTU());
                    networkInterface.setUp(ni.isUp());
                    networkInterface.setVirtual(ni.isVirtual());
                    networkInterface.setLoopback(ni.isLoopback());
                    networkInterface.setPointToPoint(ni.isPointToPoint());
                    networkInterface.setSupportsMultiCast(ni.supportsMulticast());

                    if (ni.getParent() != null) {
                        networkInterface.setChildOfDisplayName(ni.getParent().getDisplayName());
                    }

                    network.addNetworkInterface(networkInterface);
                }
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }

            return network;
        }
    }
}
