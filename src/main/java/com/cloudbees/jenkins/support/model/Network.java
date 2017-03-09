package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Network implements Serializable, MarkdownFile {
    List<NetworkInterface> networkInterfaceList = new ArrayList<>();

    public void addNetworkInterface(NetworkInterface ni) {
        networkInterfaceList.add(ni);
    }

    public List<NetworkInterface> getNetworkInterfaceList() {
        return networkInterfaceList;
    }

    public void setNetworkInterfaceList(List<NetworkInterface> networkInterfaceList) {
        this.networkInterfaceList = networkInterfaceList;
    }

    public static class NetworkInterface implements Serializable {
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

        public String getHardwareAddress() {
            return hardwareAddress;
        }

        public void setHardwareAddress(String hardwareAddress) {
            this.hardwareAddress = hardwareAddress;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<String> getAddress() {
            return address;
        }

        public void setAddress(List<String> address) {
            this.address = address;
        }

        public boolean isUp() {
            return isUp;
        }

        public void setUp(boolean up) {
            isUp = up;
        }

        public int getMtu() {
            return mtu;
        }

        public void setMtu(int mtu) {
            this.mtu = mtu;
        }

        public boolean isVirtual() {
            return isVirtual;
        }

        public void setVirtual(boolean virtual) {
            isVirtual = virtual;
        }

        public boolean isLoopback() {
            return isLoopback;
        }

        public void setLoopback(boolean loopback) {
            isLoopback = loopback;
        }

        public boolean isPointToPoint() {
            return isPointToPoint;
        }

        public void setPointToPoint(boolean pointToPoint) {
            isPointToPoint = pointToPoint;
        }

        public boolean isSupportsMultiCast() {
            return supportsMultiCast;
        }

        public void setSupportsMultiCast(boolean supportsMultiCast) {
            this.supportsMultiCast = supportsMultiCast;
        }

        public String getChildOfDisplayName() {
            return childOfDisplayName;
        }

        public void setChildOfDisplayName(String childOfDisplayName) {
            this.childOfDisplayName = childOfDisplayName;
        }

        public List<InetAddress> getInetAddressList() {
            return inetAddressList;
        }

        public void setInetAddressList(List<InetAddress> inetAddressList) {
            this.inetAddressList = inetAddressList;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public static class InetAddress implements Serializable {
            String inetAddress;

            public String getInetAddress() {
                return inetAddress;
            }

            public void setInetAddress(String inetAddress) {
                this.inetAddress = inetAddress;
            }
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
