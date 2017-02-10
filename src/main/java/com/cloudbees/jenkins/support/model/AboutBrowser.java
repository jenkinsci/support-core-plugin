package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Created by schristou88 on 2/2/17.
 */
@Data
public class AboutBrowser implements Serializable, MarkdownFile {
    String screenSize;
    UserAgent userAgent;
    OperatingSystem operatingSystem;

    @Data
    public static class UserAgent {
        String type;
        String name;
        String family;
        String producer;
        String version;
        String raw;
    }

    @Data
    public static class OperatingSystem {
        String name;
        String family;
        String producer;
        String version;
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
