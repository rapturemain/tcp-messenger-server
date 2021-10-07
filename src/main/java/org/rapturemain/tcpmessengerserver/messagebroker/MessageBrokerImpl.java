package org.rapturemain.tcpmessengerserver.messagebroker;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.*;

@Component
@Slf4j
public final class MessageBrokerImpl implements MessageBroker {

    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    private final ConcurrentHashMap<MessagePublisher, Set<MessageSubscriber>> publishers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MessageSubscriber, ConcurrentLinkedDeque<Message<?>>> subscribers = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {

    }

    @PreDestroy
    public void stop() {
       executor.shutdown();
    }

    @Override
    @NotNull
    public MessagePublisher registerPublisher(@NotNull Set<MessageSubscriber> ignoredSubscribers) {
        MessagePublisher publisher = new MessagePublisherImpl();
        publishers.put(publisher, ignoredSubscribers);

        log.debug("Registered publisher");

        return publisher;
    }

    @Override
    @NotNull
    public MessageSubscriber registerSubscriber() {
        MessageSubscriber subscriber = new MessageSubscriberImpl();
        ConcurrentLinkedDeque<Message<?>> messageQueue = new ConcurrentLinkedDeque<>();
        subscribers.put(subscriber, messageQueue);

        log.debug("Registered subscriber");

        return subscriber;
    }

    private void unregisterPublisher(@NotNull MessagePublisher publisher) {
        publishers.remove(publisher);
        log.debug("Unregistered publisher");
    }

    private void unregisterSubscriber(@NotNull MessageSubscriber subscriber) {
        subscribers.remove(subscriber);
        log.debug("Unregistered subscriber");
    }

    private Future<?> publishMessageOfPublisher(@NotNull MessagePublisher publisher, @NotNull Message<?> message) {
        if (!publishers.containsKey(publisher)) {
            throw new PublisherNotRegisteredException();
        }
        if (executor.isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        Set<MessageSubscriber> ignoredSubscribers = publishers.get(publisher);
        return executor.submit(() -> {
            subscribers.forEach((subscriber, queue) -> {
                if (!ignoredSubscribers.contains(subscriber)) {
                    queue.add(message);
                }
            });
            log.debug("Message published");
        });
    }

    private Future<?> publishMessageToSubscriber(@NotNull Message<?> message, MessageSubscriber subscriber) {
        if (executor.isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        return executor.submit(() -> {
            ConcurrentLinkedDeque<Message<?>> queue = subscribers.get(subscriber);
            if (queue == null) {
                throw new SubscriberNotRegisteredException();
            }
            queue.add(message);
            log.debug("Message published privately");
        });
    }

    @Nullable
    private Message<?> peekMessageForSubscriber(@NotNull MessageSubscriber subscriber) {
        if (!subscribers.containsKey(subscriber)) {
            throw new SubscriberNotRegisteredException();
        }
        return subscribers.get(subscriber).peek();
    }

    @Nullable
    private Message<?> pollMessageForSubscriber(@NotNull MessageSubscriber subscriber) {
        if (!subscribers.containsKey(subscriber)) {
            throw new SubscriberNotRegisteredException();
        }
        return subscribers.get(subscriber).poll();
    }

    public final class MessagePublisherImpl implements MessagePublisher {

        private MessagePublisherImpl() {

        }

        @Override
        public Future<?> publishMessage(@NotNull Message<?> message) {
            return publishMessageOfPublisher(this, message);
        }

        @Override
        public Future<?> publishToSubscriber(@NotNull Message<?> message, @NotNull MessageSubscriber subscriber) {
            return publishMessageToSubscriber(message, subscriber);
        }

        @Override
        public void unregister() {
            unregisterPublisher(this);
        }
    }

    public final class MessageSubscriberImpl implements MessageSubscriber {

        private MessageSubscriberImpl() {

        }

        @Override
        @Nullable
        public Message<?> peekMessage() {
            return peekMessageForSubscriber(this);
        }

        @Override
        @Nullable
        public Message<?> pollMessage() {
            return pollMessageForSubscriber(this);
        }

        @Override
        public void unregister() {
            unregisterSubscriber(this);
        }
    }
}
