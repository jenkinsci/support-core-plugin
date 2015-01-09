package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Utility for helping to write tests.
 *
 * @author schristou88
 * @since 2.26
 */
public class SupportTestUtils {

  /**
   * Invoke a component, and return the component contents as a String.
   */
  public static String invokeComponentToString (final Component component) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    component.addContents(
            new Container() {
              @Override
              public void add(@CheckForNull Content content) {
                try {
                  content.writeTo(baos);
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            });

    return baos.toString();
  }
}