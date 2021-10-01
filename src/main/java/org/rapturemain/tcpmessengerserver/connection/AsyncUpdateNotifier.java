package org.rapturemain.tcpmessengerserver.connection;

import org.jetbrains.annotations.NotNull;
import org.rapturemain.tcpmessengerserver.messagebroker.MessageSubscriber;

import java.io.InputStream;

public interface AsyncUpdateNotifier {
    @NotNull UpdateNotifierContext subscribeForUpdates(InputStream socketIS, MessageSubscriber subscriber);
    void unsubscribeForUpdates(@NotNull UpdateNotifierContext context);
}
