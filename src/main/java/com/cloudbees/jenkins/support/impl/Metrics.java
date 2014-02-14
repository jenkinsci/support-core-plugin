package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.SupportContext;
import com.codahale.metrics.MetricRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import jenkins.model.Jenkins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Metrics from the different nodes.
 *
 * @author Stephen Connolly
 */
@Extension
public class Metrics extends Component {

    @Override
    @NonNull
    public String getDisplayName() {
        return "Metrics";
    }

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new MetricsContent("nodes/master/metrics.json", com.codahale.metrics.jenkins.Metrics.metricRegistry()));
        for (final Node node : Jenkins.getInstance().getNodes()) {
            result.add(new RemoteMetricsContent("nodes/slave/" + node.getDisplayName() + "/metrics.json", node));
        }
    }

    private static class RemoteMetricsContent extends Content {

        private final Node node;

        public RemoteMetricsContent(String name, Node node) {
            super(name);
            this.node = node;
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            VirtualChannel channel = node.getChannel();
            if (channel == null) {
                os.write("\"N/A\"".getBytes("utf-8"));
            } else {
                try {
                    os.write(channel.call(new GetMetricsResult()));
                } catch (InterruptedException e) {
                    throw new IOException2(e);
                }
            }
        }

    }

    private static class GetMetricsResult implements Callable<byte[], RuntimeException> {
        public byte[] call() throws RuntimeException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                new MetricsContent("", null).writeTo(bos);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return bos.toByteArray();
        }
    }

}
