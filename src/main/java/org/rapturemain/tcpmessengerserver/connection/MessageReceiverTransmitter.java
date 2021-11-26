package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengermessageframework.message.MessageEncoderDecoder;
import org.rapturemain.tcpmessengermessageframework.message.base.BooleanEntry;
import org.rapturemain.tcpmessengermessageframework.message.base.StringEntry;
import org.rapturemain.tcpmessengermessageframework.message.base.TimestampEntry;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;
import org.rapturemain.tcpmessengermessageframework.message.messages.MessageFormatException;
import org.rapturemain.tcpmessengermessageframework.message.messages.chat.ChatMessage;
import org.rapturemain.tcpmessengermessageframework.message.messages.system.*;
import org.rapturemain.tcpmessengerserver.user.Name;
import org.rapturemain.tcpmessengerserver.user.User;
import org.rapturemain.tcpmessengerserver.utils.IdentityWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Calendar;
import java.util.Queue;
import java.util.concurrent.*;

@Slf4j
@Component
public class MessageReceiverTransmitter {

    private final static int THREAD_COUNT = 16;
    private final static int BYTE_BUFFER_SIZE = 1024 * 1024 * 8;

    private final MessageEncoderDecoder messageEncoderDecoder;
    private final SocketRegistrationService socketRegistrationService;
    private final UserRegistrationService userRegistrationService;
    private final SocketMessageCollector socketMessageCollector;
    private final ExecutorService executor = ForkJoinPool.commonPool();

    @Autowired
    private ExceptionHandler exceptionHandler;

    private final ConcurrentHashMap<IdentityWrapper<SocketChannel>, CompletableFuture<?>> futuresRead = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IdentityWrapper<SocketChannel>, CompletableFuture<?>> futuresWrite = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IdentityWrapper<SocketChannel>, ConcurrentLinkedQueue<MessageWrapper>> messages = new ConcurrentHashMap<>();

    @Autowired
    public MessageReceiverTransmitter(MessageEncoderDecoder messageEncoderDecoder, SocketRegistrationService socketRegistrationService, UserRegistrationService userRegistrationService, SocketMessageCollector socketMessageCollector) {
        this.messageEncoderDecoder = messageEncoderDecoder;
        this.socketRegistrationService = socketRegistrationService;
        this.userRegistrationService = userRegistrationService;
        this.socketMessageCollector = socketMessageCollector;
    }

    public void handleRead(SocketChannel socket, Runnable callback) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        futuresRead.compute(wrapper, (key, future) -> future == null ?
                CompletableFuture.runAsync(() -> readSocket(socket)).thenRunAsync(callback) :
                future.thenRunAsync(() -> readSocket(socket)).thenRunAsync(callback)
        );
    }

    public void handleWrite(SocketChannel socket, Runnable callback) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        futuresWrite.compute(wrapper, (key, future) -> future == null ?
                CompletableFuture.runAsync(() -> sendMessage(wrapper)).thenRunAsync(callback) :
                future.thenRunAsync(() -> sendMessage(wrapper)).thenRunAsync(callback)
        );
    }

    public boolean hasMessageFor(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        return messages.get(wrapper) != null && messages.get(wrapper).peek() != null;
    }

    private void sendMessage(IdentityWrapper<SocketChannel> socket) {
        if (messages.get(socket) == null && messages.get(socket).peek() == null) {
            return;
        }
        MessageWrapper wrapper = messages.get(socket).peek();
        if (wrapper == null) {
            return;
        }
        UnsafeRunnable runnable = () -> {
            if (wrapper.getMessage().remaining() > 0) {
                socket.getData().write(wrapper.getMessage());
            } else {
                messages.get(socket).poll();
            }
        };
        if (wrapper.isHandleExceptions()) {
            exceptionHandler.executeSocketTaskSafe(socket.getData(), runnable);
        } else {
            try {
                runnable.run();
            } catch (Exception e) {
                // Do nothing
            }
        }
    }

    private void readSocket(SocketChannel socket) {
        exceptionHandler.executeSocketTaskSafe(socket, () -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE);
            byteBuffer.mark();

            while (socket.read(byteBuffer) >= BYTE_BUFFER_SIZE) {
                handleBuffer(socket, byteBuffer);
                byteBuffer.position(0);
                byteBuffer.mark();
            }

            handleBuffer(socket, byteBuffer);
        });
    }

    private void handleBuffer(SocketChannel socket, ByteBuffer byteBuffer) throws IOException, ConnectionResetException {
        Message<?> message;
        byteBuffer.limit(byteBuffer.position());
        while ((message = socketMessageCollector.getMessageIfExists(socket, byteBuffer)) != null) {
            handleInput(socket, message);
        }
        byteBuffer.limit(BYTE_BUFFER_SIZE);
    }

    public void onConnect() {
        onUserConnected();
    }

    public void onDisconnect(SocketChannel socket) {
        onUserDisconnected(socket);
    }

    public void sendMessageToAll(Message<?> message) {
        socketRegistrationService.executeForEach(socket -> sendMessage(socket, message, false));
    }

    public void sendMessage(SocketChannel socket, Message<?> message, boolean doNotHandleException) {
        exceptionHandler.executeSocketTaskSafe(socket, () -> {
            IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            messageEncoderDecoder.encode(message, dataOutputStream);
            ByteBuffer buffer = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
            messages.computeIfAbsent(wrapper, key -> new ConcurrentLinkedQueue<>())
                    .add(MessageWrapper.builder()
                            .message(buffer)
                            .handleExceptions(!doNotHandleException).build()
                    );
        });

    }

    private void handleInput(SocketChannel socket, Message<?> message) throws ConnectionResetException {
        if (!userRegistrationService.isRegistered(socket)) {
            handleFirstRegistrationAndResponse(socket, message);
            return;
        }
        if (message instanceof SystemMessage) {
            handleSystemMessage((SystemMessage<?>) message);
            return;
        }
        if (message instanceof ChatMessage) {
            ChatMessage<?> chatMessage = (ChatMessage<?>) message;
            chatMessage.setSenderName(new StringEntry(userRegistrationService.getUserForSocket(socket).getName().getName()));
            chatMessage.setTimestamp(new TimestampEntry(Calendar.getInstance().getTimeInMillis()));
        }
        executor.submit(() -> sendMessageToAll(message));
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

    private void handleFirstRegistrationAndResponse(SocketChannel socket, Message<?> message) throws ConnectionResetException {
        try {
            if (handleFirstRegistration(socket, message)) {
                Message<?> msg = RegistrationResponseMessage.builder()
                        .success(new BooleanEntry(true))
                        .name(new StringEntry(userRegistrationService.getUserForSocket(socket).getName().getName()))
                        .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                        .build();
                executor.submit(() -> sendMessage(socket, msg, false));
                onUserRegistered(socket);
            }
        } catch (UnregisteredException e) {
            Message<?> msg = RegistrationResponseMessage.builder()
                    .success(new BooleanEntry(false))
                    .message(new StringEntry("Connection not registered"))
                    .build();
            executor.submit(() -> sendMessage(socket, msg, false));
        } catch (SocketRegistrationException e) {
            Message<?> msg = RegistrationResponseMessage.builder()
                    .success(new BooleanEntry(false))
                    .message(new StringEntry("Credentials not available"))
                    .build();
            executor.submit(() -> sendMessage(socket, msg, false));
        }
    }

    private boolean handleFirstRegistration(SocketChannel socket, Message<?> message) throws UnregisteredException, SocketRegistrationException, ConnectionResetException {
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

    private void handleRegistrationMessage(SocketChannel socket, RegistrationRequestMessage message) throws SocketRegistrationException {
        if (message.getName() == null || message.getName().getString() == null || message.getName().getString().equals("")) {
            throw new SocketRegistrationException();
        }
        User user = User.builder()
                .name(new Name(message.getName().getString()))
                .build();
        userRegistrationService.register(socket, user);
    }

    private void onUserConnected() {
        Message<?> message = UserConnectedMessage.builder()
                .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                .build();
        executor.submit(() -> sendMessageToAll(message));
    }

    private void onUserRegistered(SocketChannel socket) {
        Message<?> message = UserRegisteredMessage.builder()
                .name(new StringEntry(userRegistrationService.getUserForSocket(socket).getName().getName()))
                .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                .build();
        executor.submit(() -> sendMessageToAll(message));
    }

    private void onUserDisconnected(SocketChannel socket) {
        StringEntry name = null;
        if (userRegistrationService.isRegistered(socket)) {
            name = new StringEntry(userRegistrationService.getUserForSocket(socket).getName().getName());
        }
        Message<?> message = UserDisconnectedMessage.builder()
                .name(name)
                .timestamp(new TimestampEntry(Instant.now().toEpochMilli()))
                .build();
        executor.submit(() -> sendMessageToAll(message));
    }
}
