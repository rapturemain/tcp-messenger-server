package org.rapturemain.tcpmessengerserver.server;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengerserver.connection.ConnectionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class Server {

    private static final int SERVER_PORT = 25565;

    private final ConnectionHandler connectionHandler;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean stopped = false;

    @Autowired
    public Server(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    @PostConstruct
    public void start() {
        executor.execute(this::startInner);
    }

    @PreDestroy
    public void stop() {
        stopped = true;
    }

    private void startInner() {
        Selector selector = null;
        ServerSocketChannel serverSocket = null;
        try {
            selector = Selector.open();
            serverSocket = ServerSocketChannel.open();
        } catch (IOException e) {
            log.error("Cannot create server socket", e);
            System.exit(1);
        }

        try {
            serverSocket.bind(new InetSocketAddress(SERVER_PORT));
            serverSocket.configureBlocking(false);
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            log.info("Running server on port {}", serverSocket.socket().getLocalPort());
        } catch (IOException e) {
            log.error("Cannot bing server socket to the port {}", SERVER_PORT, e);
            System.exit(1);
        }


        while (!stopped) {
            try {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys) {
                    if (key.isAcceptable()) {
                        SocketChannel socketChannel = serverSocket.accept();
                        socketChannel.configureBlocking(false);
                        connectionHandler.handleConnection(socketChannel);
                    }
                }
            } catch (IOException e) {
                log.error("Error while handling connection", e);
            }
        }
    }

}
