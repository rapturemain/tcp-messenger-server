package org.rapturemain.tcpmessengerserver.connection;

import org.rapturemain.tcpmessengermessageframework.message.messages.system.PingRequest;
import org.rapturemain.tcpmessengerserver.messagebroker.MessageBroker;
import org.rapturemain.tcpmessengerserver.messagebroker.MessagePublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class SocketStateVerifierImpl implements SocketStateVerifier {

    private static final int RETRY_RATE = 15;

    private final MessageBroker messageBroker;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MessagePublisher publisher;
    private volatile boolean stopped = false;

    @Autowired
    public SocketStateVerifierImpl(MessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    @PostConstruct
    public void start() {
        publisher = messageBroker.registerPublisher();
        executor.execute(() -> {
            while (!stopped) {
                try {
                    TimeUnit.SECONDS.sleep(RETRY_RATE);
                } catch (InterruptedException e) {
                    // do nothing
                }

                verifySockets();
            }
        });
    }

    @PreDestroy
    public void stop() {
        stopped = true;
        publisher.unregister();
    }

    @Override
    public void verifySockets() {
        publisher.publishMessage(new PingRequest());
    }
}
