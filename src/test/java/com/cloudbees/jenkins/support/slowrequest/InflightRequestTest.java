package com.cloudbees.jenkins.support.slowrequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.RootAction;
import hudson.util.HttpResponses;
import java.net.URL;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * @author schristou88
 */
@WithJenkins
class InflightRequestTest {

    @Test
    @Issue("JENKINS-24671")
    void verifyUsernameInflightRequest(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        JenkinsRule.WebClient webClient = j.createWebClient().login("bob", "bob");
        MockSlowURLCall.seconds = 5;
        webClient.goTo("mockSlowURLCall/submit");
        InflightRequest request = MockSlowURLCall.request;
        assertNotNull(request);
        assertEquals("bob", request.userName);
        assertEquals(webClient.getContextPath() + "mockSlowURLCall/submit", request.url);
    }

    @Test
    @Issue("JENKINS-24671")
    void verifyRefererHeaderFromInflightRequest(JenkinsRule j) throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();

        URL refererUrl = new URL(webClient.getContextPath() + "mockSlowURLCall/submitReferer");

        ((HtmlPage) webClient.getPage(refererUrl)).getHtmlElementById("link").click();

        InflightRequest request = MockSlowURLCall.request;
        assertEquals(refererUrl.toString(), request.referer);
    }

    @TestExtension
    public static class MockSlowURLCall implements RootAction {
        public static int seconds;
        public static InflightRequest request;

        public HttpResponse doSubmitReferer(StaplerRequest2 req, StaplerResponse2 rsp) {
            String literalHTML = "<html>" + "  <body>"
                    + "    <a href='"
                    + req.getContextPath() + "/mockSlowURLCall/submit" + "' id='link'>link</a>" + "  </body>"
                    + "</html>";
            return HttpResponses.literalHtml(literalHTML);
        }

        public HttpResponse doSubmit(StaplerRequest2 req, StaplerResponse2 rsp) {
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
