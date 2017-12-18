/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Command;
import hudson.remoting.Request;
import hudson.remoting.Response;
import hudson.security.Permission;
import hudson.slaves.ComputerListener;
import hudson.slaves.OfflineCause;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;

@Extension
public class SlaveCommandStatistics extends Component {

    @Override
    public String getDisplayName() {
        return "Agent Command Statistics";
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(Container container) {
        // TODO record contents
    }

    @Extension
    public static class ComputerListenerImpl extends ComputerListener {

        @Override
        public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
            channel.addListener(new Channel.Listener() {

                @Override
                public void onWrite(Channel channel, Command cmd, long blockSize) {
                    // TODO
                }

                @Override
                public void onRead(Channel channel, Command cmd, long blockSize) {
                    // TODO
                    if (cmd instanceof Response) {
                        Response rsp = (Response) cmd;
                        Request req = rsp.getRequest();
                        long totalTime = rsp.getTotalTime();
                        if (req != null && totalTime != 0) {
                            // TODO
                        }
                    }
                }

            });
        }

        @Override
        public void onOffline(Computer c, OfflineCause cause) {
            // TODO
        }

    }

}
