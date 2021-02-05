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

import static com.cloudbees.jenkins.support.impl.ReverseProxy.FORWARDED_HEADERS;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReverseProxyTest {

    private static final String HEADER_VALUE = "value";
    private static String expectedMessage(String header, ReverseProxy.Trilean value) {
        return String.format("Detected `%s` header: %s", header, value);
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        for (String header : FORWARDED_HEADERS) {
            when(staplerRequest.getHeader(header)).thenReturn(HEADER_VALUE);
        }

        subject.addContents(container);

        for (String header : FORWARDED_HEADERS) {
            assertThat(baos.toString(), containsString(expectedMessage(header, ReverseProxy.Trilean.TRUE)));
        }
    }

    @Test
    public void addContents_NoHeader() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Container container = createContainer(baos);
        for (String header : FORWARDED_HEADERS) {
            when(staplerRequest.getHeader(header)).thenReturn(null);
        }

        subject.addContents(container);

        for (String header : FORWARDED_HEADERS) {
            assertThat(baos.toString(), containsString(expectedMessage(header, ReverseProxy.Trilean.FALSE)));
        }
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

        for (String header : FORWARDED_HEADERS) {
            assertThat(baos.toString(), containsString(expectedMessage(header, ReverseProxy.Trilean.UNKNOWN)));
        }
    }

    private static Container createContainer(OutputStream os) {
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
