package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.rapturemain.tcpmessengerserver.messagebroker.MessageSubscriber;
import org.rapturemain.tcpmessengerserver.utils.DefaultWrapper;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class AsyncUpdateNotifierImpl implements AsyncUpdateNotifier {

    private static final int THREAD_COUNT = 1;

    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);


    ConcurrentLinkedDeque<DefaultWrapper<UpdateNotifierContext>> contexts = new ConcurrentLinkedDeque<>();

    @PostConstruct
    public void start() {
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.execute(() -> {
                while (!executor.isShutdown()) {
                    try {
                        TimeUnit.MICROSECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        // do nothing
                    }

                    DefaultWrapper<UpdateNotifierContext> wrapper = contexts.poll();

                    if (wrapper == null) {
                        continue;
                    }

                    UpdateNotifierContext context = wrapper.getData();

                    if (!context.getIsWorking().get() && (subscriberReady(context) || socketReady(context))) {
                        synchronized (context.getIsWorking()) {
                            context.getIsWorking().notifyAll();
                        }
                    }

                    contexts.add(wrapper);
                }
            });
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdown();
    }

    @Override
    @NotNull
    public UpdateNotifierContext subscribeForUpdates(InputStream socketIS, MessageSubscriber subscriber) {
        UpdateNotifierContext updateNotifierContext = UpdateNotifierContext.builder()
                .socketInputStream(socketIS)
                .subscriber(subscriber)
                .isWorking(new AtomicBoolean(false))
                .build();
        contexts.add(new DefaultWrapper<>(updateNotifierContext));

        log.debug("Context subscribed");

        return updateNotifierContext;
    }

    @Override
    public void unsubscribeForUpdates(@NotNull UpdateNotifierContext context) {
        contexts.remove(new DefaultWrapper<>(context));
        log.debug("Context unsubscribed");
    }

    private boolean subscriberReady(@NotNull UpdateNotifierContext context) {
        return context.getSubscriber().peekMessage() != null;
    }

    private boolean socketReady(@NotNull UpdateNotifierContext context) {
        try {
            return context.getSocketInputStream().available() > 0;
        } catch (IOException e) {
            return false;
        }

    }
}
