package com.cloudbees.jenkins.support.model;

import hudson.Util;
import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by schristou88 on 2/14/17.
 */
@Data
public class Network implements Serializable, MarkdownFile {
    List<NetworkInterface> networkInterfaceList = new ArrayList<>();

    public void addNetworkInterface(NetworkInterface ni) {
        networkInterfaceList.add(ni);
    }

    @Data
    public static class NetworkInterface {
        String hardwareAddress;
        String displayName;
        List<String> address = new ArrayList<>();
        boolean isUp;
        int mtu;
        boolean isVirtual;
        boolean isLoopback;
        boolean isPointToPoint;
        boolean supportsMultiCast;
        String childOfDisplayName;
        List<InetAddress> inetAddressList = new ArrayList<>();
        int index;

        public void addInetAddress(InetAddress address) {
            inetAddressList.add(address);
        }

        @Data
        public static class InetAddress {
            String inetAddress;
        }
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        for (NetworkInterface ni : networkInterfaceList) {
            out.println("-----------");
            out.println(" * Name " + ni.displayName);
            out.println(" ** Hardware Address - " + ni.hardwareAddress);
            out.println(" ** Index - " + ni.index);

            for (NetworkInterface.InetAddress address : ni.inetAddressList) {
                out.println(" ** InetAddress - " + address);
            }

            out.println(" ** MTU - " + ni.mtu);
            out.println(" ** Is Up - " + ni.isUp);
            out.println(" ** Is Virtual - " + ni.isVirtual);
            out.println(" ** Is Loopback - " + ni.isLoopback);
            out.println(" ** Is Point to Point - " + ni.isPointToPoint);
            out.println(" ** Supports multicast - " + ni.supportsMultiCast);
            out.println(" ** Child of - " + ni.childOfDisplayName);
        }
    }
}
