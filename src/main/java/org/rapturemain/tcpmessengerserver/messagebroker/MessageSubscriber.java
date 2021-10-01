package org.rapturemain.tcpmessengerserver.messagebroker;

import org.jetbrains.annotations.Nullable;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;

public interface MessageSubscriber {

    @Nullable
    Message<?> peekMessage();

    @Nullable
    Message<?> pollMessage();

    void unregister();
}
