package org.rapturemain.tcpmessengerserver.messagebroker;

import org.jetbrains.annotations.NotNull;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;

public interface MessagePublisher {

    void publishMessage(@NotNull Message<?> message);

    void publishToSubscriber(@NotNull Message<?> message, @NotNull MessageSubscriber subscriber);

    void unregister();
}
