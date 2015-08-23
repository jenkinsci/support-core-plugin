package com.cloudbees.jenkins.support.api;

import java.io.IOException;

/**
 * Limit size for a specific file has been reached, and has been truncated.
 *
 * @author schristou
 */
public class TruncationException extends IOException {
    public TruncationException(String message) {
        super(message);
    }
}
