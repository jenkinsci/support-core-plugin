package com.cloudbees.jenkins.support.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Created by schristou88 on 2/2/17.
 */
public class SupportUtils {
    public static String toString(Object yaml) throws JsonProcessingException {
        YAMLFactory yf = new YAMLFactory();
        ObjectMapper om = new ObjectMapper(yf);
        return om.writerWithDefaultPrettyPrinter().writeValueAsString(yaml);
    }
}
