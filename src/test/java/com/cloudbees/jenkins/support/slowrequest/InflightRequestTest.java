package com.cloudbees.jenkins.support.slowrequest;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpRequest;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.ClientInfoStatus;
import java.util.ResourceBundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author schristou88
 */
public class InflightRequestTest {
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test
    @Bug(24671)
    public void verifyUsernameInflightRequest() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        JenkinsRule.WebClient webClient = r.createWebClient().login("bob", "bob");
        MockSlowURLCall.seconds = 5;
        webClient.goTo("mockSlowURLCall/submit");
        InflightRequest request = MockSlowURLCall.request;
        assertNotNull(request);
        assertEquals("bob", request.userName);
        assertEquals(webClient.getContextPath() + "mockSlowURLCall/submit", request.url);
    }

    @Test
    @Bug(24671)
    public void verifyRefererHeaderFromInflightRequest() throws Exception {
        JenkinsRule.WebClient webClient = r.createWebClient();

        URL refererUrl = new URL(webClient.getContextPath() + "mockSlowURLCall/submitReferer");

        ((HtmlPage) webClient.getPage(refererUrl)).getHtmlElementById("link").click();

        InflightRequest request = MockSlowURLCall.request;
        assertEquals(refererUrl.toString(), request.referer);
    }

    @TestExtension
    public static class MockSlowURLCall implements RootAction {
        public static int seconds;
        public static InflightRequest request;

        public HttpResponse doSubmitReferer(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
            String literalHTML = "<html>" +
                                 "  <body>" +
                                 "    <a href='" + req.getContextPath() + "/mockSlowURLCall/submit"+ "' id='link'>link</a>" +
                                 "  </body>" +
                                 "</html>";
            return HttpResponses.html(literalHTML);
        }

        public HttpResponse doSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
            request = new InflightRequest(req);
            return HttpResponses.redirectTo("..");
        }

        public String getIconFileName() {
            return "";
        }

        public String getDisplayName() {
            return "MockSlowURLCall";
        }

        public String getUrlName() {
            return "mockSlowURLCall";
        }
    }
}