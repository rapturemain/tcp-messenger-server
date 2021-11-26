package org.rapturemain.tcpmessengerserver.connection;

@FunctionalInterface
public interface UnsafeRunnable {
    void run() throws Exception;
}
