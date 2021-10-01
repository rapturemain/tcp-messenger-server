package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengermessageframework.message.MessageEncoderDecoder;
import org.rapturemain.tcpmessengermessageframework.message.base.BooleanEntry;
import org.rapturemain.tcpmessengermessageframework.message.base.StringEntry;
import org.rapturemain.tcpmessengermessageframework.message.base.TimestampEntry;
import org.rapturemain.tcpmessengermessageframework.message.messages.ChatMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.ConnectionResetMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;
import org.rapturemain.tcpmessengermessageframework.message.messages.SystemMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.request.PingRequest;
import org.rapturemain.tcpmessengermessageframework.message.messages.request.RegistrationRequestMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.response.PingResponse;
import org.rapturemain.tcpmessengermessageframework.message.messages.response.RegistrationResponseMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.system.UserConnectedMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.system.UserDisconnectedMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.system.UserRegisteredMessage;
import org.rapturemain.tcpmessengerserver.messagebroker.MessageBroker;
import org.rapturemain.tcpmessengerserver.messagebroker.MessagePublisher;
import org.rapturemain.tcpmessengerserver.messagebroker.MessageSubscriber;
import org.rapturemain.tcpmessengerserver.user.Name;
import org.rapturemain.tcpmessengerserver.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.Calendar;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class ConnectionHandlerImpl implements ConnectionHandler {

    private final AsyncUpdateNotifier asyncUpdateNotifier;
    private final MessageBroker messageBroker;
    private final MessageEncoderDecoder messageEncoderDecoder;
    private final SocketRegistrationService socketRegistrationService;

    @Autowired
    public ConnectionHandlerImpl(AsyncUpdateNotifier asyncUpdateNotifier, MessageBroker messageBroker, MessageEncoderDecoder messageEncoderDecoder, SocketRegistrationService socketRegistrationService) {
        this.asyncUpdateNotifier = asyncUpdateNotifier;
        this.messageBroker = messageBroker;
        this.messageEncoderDecoder = messageEncoderDecoder;
        this.socketRegistrationService = socketRegistrationService;
    }

    @Override
    public void handleConnection(Socket socket) {
        log.info("Handling connection {}, {}", socket.getInetAddress(), socket.getPort());
        new Thread(() -> handleConnectionInternal(socket)).start();
    }

    private void handleConnectionInternal(Socket socket) {
        InputStream socketIS;
        DataInputStream dis;
        DataOutputStream dos;

        try {
            socket.setSoTimeout(30000);
            socketIS = socket.getInputStream();
            dis = new DataInputStream(socketIS);
            dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            log.error("Client [address {}, port {}] attempted connection, but failed to get Input/Output streams", socket.getInetAddress(), socket.getPort(), e);
            return;
        }

        MessagePublisher publisher = messageBroker.registerPublisher();
        MessageSubscriber subscriber = messageBroker.registerSubscriber();
        UpdateNotifierContext unc = asyncUpdateNotifier.subscribeForUpdates(socketIS, subscriber);

        onUserConnected(publisher);

        try {
            while (true) {
                synchronized (unc.getIsWorking()) {
                    try {
                        unc.getIsWorking().wait();
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                    unc.getIsWorking().set(true);

                    handleInput(socket, dis, publisher, subscriber);
                    handleOutput(subscriber, dos);

                    unc.getIsWorking().set(false);
                }
            }
        } catch (IOException e) {
            log.info("Client [address {}, port {}] unavailable, closing connection", socket.getInetAddress(), socket.getPort());
            log.debug("Client [address {}, port {}] unavailable", socket.getInetAddress(), socket.getPort(), e);
        } catch (ConnectionResetException connectionResetException) {
            log.info("Client [address {}, port {}] closed connection", socket.getInetAddress(), socket.getPort());
        } finally {
            onUserDisconnected(socket, publisher);

            socketRegistrationService.unregister(socket);
            asyncUpdateNotifier.unsubscribeForUpdates(unc);
            subscriber.unregister();
            publisher.unregister();
        }

        try {
            socket.close();
        } catch (IOException e) {
            log.error("Could not close connection, socket {}:{}", socket.getInetAddress(), socket.getPort(), e);
        }
    }

    private void handleInput(Socket socket, DataInputStream dis, MessagePublisher publisher, MessageSubscriber subscriber) throws IOException, ConnectionResetException {
        Message<?> message;
        while ((message = messageEncoderDecoder.decode(dis)) != null) {
            if (!socketRegistrationService.isRegistered(socket)) {
                handleFirstRegistrationAndResponse(socket, message, publisher, subscriber);
                continue;
            }
            if (message instanceof SystemMessage) {
                handleSystemMessage((SystemMessage<?>) message);
                continue;
            }
            if (message instanceof ChatMessage) {
                ChatMessage<?> chatMessage = (ChatMessage<?>) message;
                chatMessage.setSenderName(new StringEntry(socketRegistrationService.getUserForSocket(socket).getName().getName()));
                chatMessage.setTimestamp(new TimestampEntry(Calendar.getInstance().getTimeInMillis()));
            }
            publisher.publishMessage(message);
        }
    }

    private void handleSystemMessage(SystemMessage<?> message) throws ConnectionResetException {
        handleConnectionReset(message);
    }

    private void handleConnectionReset(SystemMessage<?> message) throws ConnectionResetException {
        if (message instanceof ConnectionResetMessage) {
            throw new ConnectionResetException();
        }
    }

    private boolean isIgnoredWhenRegistration(Message<?> message) {
        return message instanceof PingRequest ||
                message instanceof PingResponse;
    }

    private void handleFirstRegistrationAndResponse(Socket socket, Message<?> message, MessagePublisher publisher, MessageSubscriber subscriber) throws ConnectionResetException {
        try {
            if (handleFirstRegistration(socket, message)) {
                publisher.publishToSubscriber(RegistrationResponseMessage.builder()
                        .success(new BooleanEntry(true))
                        .name(new StringEntry(socketRegistrationService.getUserForSocket(socket).getName().getName()))
                        .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                        .build(), subscriber);
                onUserRegistered(socket, publisher);
            }
        } catch (UnregisteredException e) {
            publisher.publishToSubscriber(RegistrationResponseMessage.builder()
                    .success(new BooleanEntry(false))
                    .message(new StringEntry("Connection not registered"))
                    .build(), subscriber);
        } catch (SocketRegistrationException e) {
            publisher.publishToSubscriber(RegistrationResponseMessage.builder()
                    .success(new BooleanEntry(false))
                    .message(new StringEntry("Credentials not available"))
                    .build(), subscriber);
        }
    }

    private boolean handleFirstRegistration(Socket socket, Message<?> message) throws UnregisteredException, SocketRegistrationException, ConnectionResetException {
        if (isIgnoredWhenRegistration(message)) {
            return false;
        }
        if (message instanceof SystemMessage<?>) {
            handleConnectionReset((SystemMessage<?>) message);
        }
        if (!(message instanceof RegistrationRequestMessage)) {
            throw new UnregisteredException();
        }
        handleRegistrationMessage(socket, (RegistrationRequestMessage) message);
        return true;
    }

    private void handleRegistrationMessage(Socket socket, RegistrationRequestMessage message) throws SocketRegistrationException {
        if (message.getName() == null || message.getName().getString() == null || message.getName().getString().equals("")) {
            throw new SocketRegistrationException();
        }
        User user = User.builder()
                .name(new Name(message.getName().getString()))
                .build();
        socketRegistrationService.register(socket, user);
    }

    private void handleOutput(MessageSubscriber subscriber, DataOutputStream dataOutputStream) throws IOException {
        Message<?> message;
        while ((message = subscriber.pollMessage()) != null) {
            messageEncoderDecoder.encode(message, dataOutputStream);
        }
        dataOutputStream.flush();
    }

    private void onUserConnected(MessagePublisher publisher) {
        publisher.publishMessage(UserConnectedMessage.builder()
                .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                .build());
    }

    private void onUserRegistered(Socket socket, MessagePublisher publisher) {
        publisher.publishMessage(UserRegisteredMessage.builder()
                .name(new StringEntry(socketRegistrationService.getUserForSocket(socket).getName().getName()))
                .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                .build());
    }

    private void onUserDisconnected(Socket socket, MessagePublisher publisher) {
        StringEntry name = null;
        if (socketRegistrationService.isRegistered(socket)) {
            name = new StringEntry(socketRegistrationService.getUserForSocket(socket).getName().getName());
        }
        publisher.publishMessage(UserDisconnectedMessage.builder()
                .name(name)
                .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                .build());
    }
}