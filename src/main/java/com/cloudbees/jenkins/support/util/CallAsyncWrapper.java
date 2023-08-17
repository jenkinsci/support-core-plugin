package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CallAsyncWrapper {

    public static <V, T extends Throwable> Future<V> callAsync(
            final VirtualChannel channel, final Callable<V, T> callable) throws IOException {
        var executorFuture = Computer.threadPoolForRemoting.submit(() -> channel.callAsync(callable));
        try {
            return executorFuture.get(SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        } catch (TimeoutException te) {
            executorFuture.cancel(true);
            throw new IOException(te);
        }
    }

    private CallAsyncWrapper() {}
}
