package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.cloudbees.jenkins.support.impl.ReverseProxy.X_FORWARDED_FOR_HEADER;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class ReverseProxyTest {

  private static final String HEADER_VALUE = "value";
  private static final String X_FORWARDED_FOR_HEADER_FOUND_MESSAGE = "Detected `X-Forwarded-For` header: true";
  private static final String X_FORWARDED_FOR_HEADER_NOT_FOUND_MESSAGE = "Detected `X-Forwarded-For` header: false";

  @Rule
  public JenkinsRule r = new JenkinsRule();

  private ReverseProxy subject;

  @Test
  public void addContents() throws Exception {
    MockHttpURLConnection mockHttpURLConnection = new MockHttpURLConnection(r.getURL());
    subject = createReverseProxy(mockHttpURLConnection);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);
    mockHttpURLConnection.addHeader(X_FORWARDED_FOR_HEADER, HEADER_VALUE);
    getJenkinsLocationConfiguration().setUrl(r.getURL().toString());

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_FOUND_MESSAGE));
  }

  @Test
  public void addContents_NoHeader() throws Exception {
    MockHttpURLConnection mockHttpURLConnection = new MockHttpURLConnection(r.getURL());
    subject = createReverseProxy(mockHttpURLConnection);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);
    getJenkinsLocationConfiguration().setUrl(r.getURL().toString());

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_NOT_FOUND_MESSAGE));
  }

  @Test
  public void addContents_NoRootURL() throws Exception {
    MockHttpURLConnection mockHttpURLConnection = new MockHttpURLConnection(r.getURL());
    subject = createReverseProxy(mockHttpURLConnection);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);
    mockHttpURLConnection.addHeader(X_FORWARDED_FOR_HEADER, HEADER_VALUE);
    getJenkinsLocationConfiguration().setUrl(null);

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_NOT_FOUND_MESSAGE));
  }

  @Test
  public void addContents_BadRequest() throws Exception {
    MockHttpURLConnection mockHttpURLConnection = new MockHttpURLConnection(r.getURL());
    mockHttpURLConnection.setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST);
    subject = createReverseProxy(mockHttpURLConnection);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Container container = createContainer(baos);
    mockHttpURLConnection.addHeader(X_FORWARDED_FOR_HEADER, HEADER_VALUE);
    getJenkinsLocationConfiguration().setUrl(r.getURL().toString());

    subject.addContents(container);

    assertThat(baos.toString(), containsString(X_FORWARDED_FOR_HEADER_NOT_FOUND_MESSAGE));
  }

  private static JenkinsLocationConfiguration getJenkinsLocationConfiguration() {
    JenkinsLocationConfiguration jenkinsLocationConfiguration = JenkinsLocationConfiguration.get();

    if (jenkinsLocationConfiguration == null) {
      throw new RuntimeException("Cannot find JenkinsLocationConfiguration");
    }
    return jenkinsLocationConfiguration;
  }

  private static ReverseProxy createReverseProxy(MockHttpURLConnection mockHttpURLConnection) {
    return new ReverseProxy() {
      @Override
      protected HttpURLConnection getHttpURLConnection(String url) {
        return mockHttpURLConnection;
      }
    };
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

  private static final class MockHttpURLConnection extends HttpURLConnection {

    private final Map<String, String> headers = new HashMap<>();
    private Integer responseCode = HttpURLConnection.HTTP_OK;

    private MockHttpURLConnection(URL url) {
      super(url);
    }

    void addHeader(String key, String value) {
      headers.put(key, value);
    }

    void setResponseCode(Integer responseCode) {
      this.responseCode = responseCode;
    }

    @Override
    public void disconnect() {}

    @Override
    public boolean usingProxy() {
      return false;
    }

    @Override
    public void connect() {}

    @Override
    public int getResponseCode() {
      return responseCode;
    }

    @Override
    public String getHeaderField(String key) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(key))
          .findFirst()
          .map(Map.Entry::getValue)
          .orElse(null);
    }
  }
}
