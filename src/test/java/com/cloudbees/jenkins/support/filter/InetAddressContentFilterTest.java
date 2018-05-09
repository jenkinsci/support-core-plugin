/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.filter;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class InetAddressContentFilterTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    private final String input;
    private final boolean shouldFilter;

    public InetAddressContentFilterTest(String input, boolean shouldFilter) {
        this.input = input;
        this.shouldFilter = shouldFilter;
    }

    @Parameters(name = "{0}")
    public static Object[][] data() throws UnknownHostException {
        return new Object[][] {
                {"127.0.0.1", true},
                {"0.0.0.0", true},
                {"256.0.0.1", false},
                {"255.255.255.255", true},
                {"http://192.168.10.100/", true},
                {"192:168::1", true},
                {"ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", true},
                {"inva:lid", false},
                {"10::1", true},
                {"http://[10::1]:8080/", true},
                {InetAddress.getLocalHost().getHostAddress(), true}
        };
    }

    @Issue("JENKINS-21670")
    @Test
    public void testFilter() {
        InetAddressContentFilter filter = InetAddressContentFilter.get();
        String filtered = filter.filter(input);
        if (shouldFilter) {
            assertThat(filtered).contains("ip_").doesNotContain(input);
        } else {
            assertThat(filtered).isEqualTo(input);
        }
    }
}
