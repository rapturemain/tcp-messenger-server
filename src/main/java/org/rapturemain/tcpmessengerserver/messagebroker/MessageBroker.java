package org.rapturemain.tcpmessengerserver.messagebroker;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public interface MessageBroker {

    @NotNull
    MessagePublisher registerPublisher(@NotNull Set<MessageSubscriber> ignoredSubscribers);
    @NotNull
    default MessagePublisher registerPublisher() { return registerPublisher(Collections.emptySet()); }
    @NotNull
    default MessagePublisher registerPublisher(@NotNull MessageSubscriber ignoredSubscriber) { return registerPublisher(Set.of(ignoredSubscriber)); }

    @NotNull
    MessageSubscriber registerSubscriber();
}
