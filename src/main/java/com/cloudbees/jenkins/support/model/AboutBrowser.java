package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Created by schristou88 on 2/2/17.
 */

public class AboutBrowser implements Serializable, MarkdownFile {
    String screenSize;
    UserAgent userAgent;
    OperatingSystem operatingSystem;

    public String getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(String screenSize) {
        this.screenSize = screenSize;
    }

    public UserAgent getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(UserAgent userAgent) {
        this.userAgent = userAgent;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(OperatingSystem operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public static class UserAgent implements Serializable {
        String type;
        String name;
        String family;
        String producer;
        String version;
        String raw;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFamily() {
            return family;
        }

        public void setFamily(String family) {
            this.family = family;
        }

        public String getProducer() {
            return producer;
        }

        public void setProducer(String producer) {
            this.producer = producer;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }
    }


    public static class OperatingSystem implements Serializable {
        String name;
        String family;
        String producer;
        String version;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFamily() {
            return family;
        }

        public void setFamily(String family) {
            this.family = family;
        }

        public String getProducer() {
            return producer;
        }

        public void setProducer(String producer) {
            this.producer = producer;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public void toMarkdown(PrintWriter out) {
        out.println("Browser");
        out.println("=======");
        out.println();

        if (screenSize != null) {
            out.println("  * Screen size: " + screenSize);
        }

        out.println("  * User Agent");
        out.println("      - Type:     " + userAgent.getType());
        out.println("      - Name:     " + userAgent.getName());
        out.println("      - Family:   " + userAgent.getFamily());
        out.println("      - Producer: " + userAgent.getProducer());
        out.println("      - Version:  " + userAgent.getVersion());
        out.println("      - Raw:      `" + userAgent.getVersion().replaceAll("`", "&#96;") + '`');
        out.println("  * Operating System");
        out.println("      - Name:     " + operatingSystem.getName());
        out.println("      - Family:   " + operatingSystem.getFamily());
        out.println("      - Producer: " + operatingSystem.getProducer());
        out.println("      - Version:  " + operatingSystem.getVersion());
        out.println();
    }
}
