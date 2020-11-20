package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CallAsyncWrapper {

    public static <V,T extends Throwable> hudson.remoting.Future<V> callAsync(final VirtualChannel channel, final Callable<V,T> callable) throws IOException {

        Future<hudson.remoting.Future<V>> future = Computer.threadPoolForRemoting.submit(new java.util.concurrent.Callable<hudson.remoting.Future<V>>() {
            @Override
            public hudson.remoting.Future<V> call() throws Exception {
                return channel.callAsync(callable);
            }
        });
        try {
            return future.get(SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException(e);
        }

    }
}
