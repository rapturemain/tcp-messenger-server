package org.rapturemain.tcpmessengerserver.messagebroker;

import org.jetbrains.annotations.NotNull;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;

import java.util.concurrent.Future;

public interface MessagePublisher {

    Future<?> publishMessage(@NotNull Message<?> message);

    Future<?> publishToSubscriber(@NotNull Message<?> message, @NotNull MessageSubscriber subscriber);

    void unregister();
}
