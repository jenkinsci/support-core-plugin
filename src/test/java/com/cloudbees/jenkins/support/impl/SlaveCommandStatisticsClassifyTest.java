/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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
package com.cloudbees.jenkins.support.impl;

import static com.cloudbees.jenkins.support.impl.SlaveCommandStatistics.Statistics.classify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SlaveCommandStatisticsClassifyTest {

    @Test
    public void patternStripsEndBracketsHashAndParentheses() {
        assertEquals(
                "UserRequest:RemoteLaunchCallable",
                classify("UserRequest:RemoteLaunchCallable[cmd=[docker, exec, --env, FOO=bar]]@2b194a2b"));

        assertEquals("UserRequest:Callable", classify("UserRequest:Callable@1a2b3c4d[params]@5e6f7890(Context)"));

        assertEquals(
                "UserRequest:RemoteLaunchCallable",
                classify(
                        "UserRequest:RemoteLaunchCallable[cmd=[nohup, sh, -c, (cp script.sh script.sh.copy; sh -xe script.sh.copy)], env=[Ljava.lang.String;@284e13da]@abc123"));

        assertEquals(
                "UserRequest:UserRPCRequest:org.jenkinsci.plugins.gitclient.GitClient.addCredentials",
                classify(
                        "UserRequest:UserRPCRequest:org.jenkinsci.plugins.gitclient.GitClient.addCredentials[java.lang.String,com.cloudbees.plugins.credentials.common.StandardCredentials]@abc"));

        assertEquals(
                "UserRequest:hudson.FilePath$CallableWith",
                classify("UserRequest:hudson.FilePath$CallableWith[workspace=/home/jenkins/workspace]@7f31245a"));

        assertEquals(
                "UserRequest:com.cloudbees.jenkins.support.impl.ThreadDumps$GetThreadDump",
                classify("UserRequest:com.cloudbees.jenkins.support.impl.ThreadDumps$GetThreadDump@abc123"));

        assertEquals("UserRequest:hudson.EnvVars$GetEnvVars", classify("UserRequest:hudson.EnvVars$GetEnvVars@def456"));

        assertEquals(
                "RPCRequest:hudson.FilePath.act",
                classify(
                        "RPCRequest:hudson.FilePath.act[hudson.FilePath$FileCallable,hudson.remoting.VirtualChannel$ACL](123)"));

        assertEquals("RPCRequest:hudson.FilePath.exists", classify("RPCRequest:hudson.FilePath.exists[](456)"));

        // Legacy format (pre-jenkinsci/jenkins#9921)
        assertEquals("Response", classify("Response@456abc(hudson.remoting.Channel)"));
        assertEquals("UserRequest:RemoteLaunchCallable", classify("UserRequest:RemoteLaunchCallable@67c6eade8"));

        // Invalid hex not matched
        assertEquals("Cmd@zebra", classify("Cmd@zebra"));

        // Brackets in middle preserved ($ anchor)
        assertEquals("Prefix[arg]:Suffix", classify("Prefix[arg]:Suffix@abc"));
    }

    @Test
    public void hugeEnvVarsDeduplicateToSameKey() {
        String hugeEnv = "group1,group2,group3,%s".formatted("x".repeat(12000));
        String withHugeEnv =
                "UserRequest:RemoteLaunchCallable[cmd=[docker, exec, --env, BUILD_USER_GROUPS=%s]]@67c6eade8"
                        .formatted(hugeEnv);

        String expected = "UserRequest:RemoteLaunchCallable";
        assertEquals(expected, classify(withHugeEnv));
        assertEquals(
                expected, classify("UserRequest:RemoteLaunchCallable[cmd=[docker, --env, BUILD_NUMBER=1]]@1a2b3c4d"));
        assertEquals(
                expected, classify("UserRequest:RemoteLaunchCallable[cmd=[docker, --env, BUILD_NUMBER=2]]@5e6f7890"));
        assertEquals(
                expected,
                classify("UserRequest:RemoteLaunchCallable[cmd=[docker, --env, TIMESTAMP=%d]]@deadbeef"
                        .formatted(System.currentTimeMillis())));

    }

    @Test
    public void defenseInDepthTruncationAndBounds() {
        // Truncation: MAX_COMMAND_LENGTH = 256
        String veryLongCommand = "UserRequest:%s@1a2b3c4d".formatted("VeryLongClassName".repeat(20));
        String truncated = classify(veryLongCommand);
        assertThat(truncated.length(), lessThanOrEqualTo(259));
        assertThat(truncated, endsWith("..."));

        // Pattern bound: over 1MB gracefully degrades
        String overLimit = "Cmd[%s]@abc".formatted("x".repeat(1048577));
        assertThat(classify(overLimit).length(), lessThanOrEqualTo(259));
        assertThat(classify(overLimit), startsWith("Cmd["));

        // ReDoS safety
        assertEquals("Test", classify("Test%s]@abc".formatted("[".repeat(1000))));
        assertEquals("Test", classify("Test%s@abc".formatted("[]".repeat(500))));
        assertThat(classify("Test%s@abc".formatted("[".repeat(1000))).length(), lessThanOrEqualTo(259));
    }
}
