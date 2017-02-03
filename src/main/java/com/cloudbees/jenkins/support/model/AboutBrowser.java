package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Created by schristou88 on 2/2/17.
 */
@Data
public class AboutBrowser implements Serializable {
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
}
