package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengermessageframework.message.messages.MessageFormatException;
import org.rapturemain.tcpmessengermessageframework.message.messages.system.ConnectionResetMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

@Slf4j
@Component
public class ExceptionHandler {

    private final MessageReceiverTransmitter messageReceiverTransmitter;
    private final SocketRegistrationService registrationService;
    private final SocketMessageCollector messageCollector;
    private final UserRegistrationService userRegistrationService;

    @Autowired
    public ExceptionHandler(MessageReceiverTransmitter messageReceiverTransmitter, SocketRegistrationService registrationService, SocketMessageCollector messageCollector, UserRegistrationService userRegistrationService) {
        this.messageReceiverTransmitter = messageReceiverTransmitter;
        this.registrationService = registrationService;
        this.messageCollector = messageCollector;
        this.userRegistrationService = userRegistrationService;
    }

    public void executeSocketTaskSafe(SocketChannel socket, UnsafeRunnable runnable) {
        try {
            runnable.run();
            return;
        } catch (MessageFormatException e) {
            log.info("Client [address {}, port {}] sent wrong message, closing connection", socket.socket().getInetAddress(), socket.socket().getPort());
        } catch (SocketTimeoutException e) {
            log.info("Client [address {}, port {}] timed out, closing connection", socket.socket().getInetAddress(), socket.socket().getPort());
        } catch (IOException e) {
            log.info("Client [address {}, port {}] unavailable, closing connection", socket.socket().getInetAddress(), socket.socket().getPort());
            log.debug("Client [address {}, port {}] unavailable", socket.socket().getInetAddress(), socket.socket().getPort(), e);
        } catch (ConnectionResetException e) {
            log.info("Client [address {}, port {}] closed connection", socket.socket().getInetAddress(), socket.socket().getPort());
        } catch (Exception e) {
            log.info("Unknown exception", e);
        }

        closeSocket(socket);
    }

    public void closeSocket(SocketChannel socket) {
        messageReceiverTransmitter.sendMessage(socket, new ConnectionResetMessage(), true);
        registrationService.unregister(socket);
        messageCollector.deleteSocket(socket);
        userRegistrationService.unregister(socket);

        try {
            socket.close();
        } catch (IOException e) {
            log.error("Could not close connection, socket {}:{}", socket.socket().getInetAddress(), socket.socket().getPort(), e);
        }
    }
}
