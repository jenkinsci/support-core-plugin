package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static com.cloudbees.jenkins.support.impl.ReverseProxy.X_FORWARDED_FOR_HEADER;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReverseProxyTest {

  private static final String X_FORWARDED_FOR_HEADER_FOUND_MESSAGE = "Detected `X-Forwarded-For` header: TRUE";
  private static final String X_FORWARDED_FOR_HEADER_NOT_FOUND_MESSAGE = "Detected `X-Forwarded-For` header: FALSE";
  private static final String X_FORWARDED_FOR_HEADER_UNKNOWN_MESSAGE = "Detected `X-Forwarded-For` header: UNKNOWN";
  private static final String HEADER_VALUE = "value";

  @Rule
  public JenkinsRule r = new JenkinsRule();

  @Mock
  private StaplerRequest staplerRequest;

  private ReverseProxy subject;

  @Before
  public void setUp() {
    subject = new ReverseProxy() {
      @Override
      protected StaplerRequest getCurrentRequest() {
        return staplerRequest;
      }
    };
  }

  @Test
  public void addContents() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);
    when(staplerRequest.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(HEADER_VALUE);

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_FOUND_MESSAGE));
  }

  @Test
  public void addContents_NoHeader() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);
    when(staplerRequest.getHeader(X_FORWARDED_FOR_HEADER)).thenReturn(null);

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_NOT_FOUND_MESSAGE));
  }

  @Test
  public void addContents_NoCurrentRequest() {
    subject = new ReverseProxy() {
      @Override
      protected StaplerRequest getCurrentRequest() {
        return null;
      }
    };
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_UNKNOWN_MESSAGE));
  }

  private static Container createContainer(final OutputStream os) {
    return new Container() {
      @Override
      public void add(@CheckForNull Content content) {
        try {
          content.writeTo(os);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };
  }
}
