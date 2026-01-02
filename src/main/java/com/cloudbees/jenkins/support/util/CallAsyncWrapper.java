package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.model.Computer;
import hudson.remoting.AsyncFutureImpl;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A utility class to safely call asynchronous operations on a {@link VirtualChannel}. The primary purpose of this
 * wrapper is to address a known issue where {@code VirtualChannel.callAsync(Callable)} can block indefinitely before
 * even returning a {@link Future}, especially when the channel is congested.
 *
 * This wrapper ensures that the asynchronous call is executed in a separate thread, allowing us to impose a timeout
 * on the operation. If the operation exceeds the specified timeout, it is cancelled, and an {@link IOException} is
 * returned indicating the timeout.
 */
public final class CallAsyncWrapper {

    public static <V, T extends Throwable> Future<V> callAsync(
            final VirtualChannel channel, final Callable<V, T> callable) {
        final AsyncFutureImpl<V> resultFuture = new AsyncFutureImpl<>();

        Computer.threadPoolForRemoting.submit(() -> {
            Future<V> innerFuture = null;
            try {
                innerFuture = channel.callAsync(callable);
                try {
                    V result = innerFuture.get(SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    resultFuture.set(result);
                } catch (TimeoutException te) {
                    innerFuture.cancel(true);
                    resultFuture.set(new IOException(
                            "Operation timed out after " + SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS + "ms", te));
                } catch (Throwable t) {
                    resultFuture.set(t);
                }

            } catch (Throwable t) {
                if (innerFuture != null) {
                    innerFuture.cancel(true);
                }
                resultFuture.set(t);
            }
        });

        return resultFuture;
    }

    private CallAsyncWrapper() {}
}
