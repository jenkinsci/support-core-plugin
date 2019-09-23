package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

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

    /**
     * Invoke a component with {@link ContentFilter}, and return the component contents as a String.
     */
    public static String invokeComponentToString(final Component component, final ContentFilter filter) throws IOException {
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            component.addContents(
                new Container() {
                    @Override
                    public void add(@CheckForNull Content content) {
                        try {
                            ((PrefilteredContent) Objects.requireNonNull(content)).writeTo(baos, filter);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            return baos.toString();
        }
    }

    /**
     * Invoke a component, and return a component content as a String.
     * Useful to unit test a specific content.
     */
    public static String invokeContentToString(final Component component, final String contentName) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        component.addContents(
                new Container() {
                    @Override
                    public void add(@CheckForNull Content content) {
                        if(!contentName.equals(content.getName())) {
                            return;
                        }
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