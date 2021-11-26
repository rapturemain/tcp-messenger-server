package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengerserver.utils.IdentityWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class ConnectionHandler {

    private final SocketRegistrationService socketRegistrationService;
    private final MessageReceiverTransmitter messageReceiverTransmitter;

    private final ExecutorService executor = Executors.newFixedThreadPool(1);

    private Selector selector;
    private volatile boolean stopped = false;

    @Autowired
    public ConnectionHandler(SocketRegistrationService socketRegistrationService, MessageReceiverTransmitter messageReceiverTransmitter, ExceptionHandler exceptionHandler) {
        this.socketRegistrationService = socketRegistrationService;
        this.messageReceiverTransmitter = messageReceiverTransmitter;
    }

    @PostConstruct
    public void start() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            log.error("Cannot create selector", e);
            System.exit(1);
        }

        executor.execute(this::listenSelector);
    }

    @PreDestroy
    public void stop() {
        stopped = true;
    }

    private void listenSelector() {
        ConcurrentHashMap<SelectionKey, Object> keysRead = new ConcurrentHashMap<>();
        ConcurrentHashMap<SelectionKey, Object> keysWrite = new ConcurrentHashMap<>();
        Object dummy = new Object();
        while (!stopped) {
            try {
                selector.select();
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    IdentityWrapper<SelectionKey> wrapper = new IdentityWrapper<>(key);

                    SocketChannel socket = (SocketChannel) key.channel();
                    if (!key.isValid()) {
                        socketRegistrationService.unregister(socket);
                        messageReceiverTransmitter.onDisconnect(socket);
                        log.info("Client [address {}, port {}] disconnected", socket.socket().getInetAddress(), socket.socket().getPort());
                        return;
                    }
                    if (key.isReadable() && !keysRead.containsKey(key)) {
                        keysRead.put(key, dummy);
                        messageReceiverTransmitter.handleRead(socket, () -> keysRead.remove(key));
                    }
                    if (key.isWritable() && !keysWrite.containsKey(key) && messageReceiverTransmitter.hasMessageFor(socket)) {
                        keysWrite.put(key, dummy);
                        messageReceiverTransmitter.handleWrite(socket, () -> keysWrite.remove(key));
                    }

                    iter.remove();
                }
            } catch (IOException e) {
                log.error("Error while handling connection", e);
            }
        }
    }

    public void handleConnection(SocketChannel channel) {
        InetAddress inetAddress = channel.socket().getInetAddress();
        int port =  channel.socket().getPort();

        log.info("Handling connection {}, {}", inetAddress, port);

        try {
            socketRegistrationService.register(channel);
            channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            messageReceiverTransmitter.onConnect();
            selector.wakeup();
        } catch (IOException e) {
            socketRegistrationService.unregister(channel);
            log.error("Cannot register socket channel {}:{}", inetAddress, port);
        }
    }
}