package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengermessageframework.message.MessageEncoderDecoder;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;
import org.rapturemain.tcpmessengerserver.utils.IdentityWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SocketMessageCollector {

    private final MessageEncoderDecoder messageEncoderDecoder;

    private final ConcurrentHashMap<IdentityWrapper<SocketChannel>, byte[]> buffers = new ConcurrentHashMap<>();

    @Autowired
    public SocketMessageCollector(MessageEncoderDecoder messageEncoderDecoder) {
        this.messageEncoderDecoder = messageEncoderDecoder;
    }

    public Message<?> getMessageIfExists(SocketChannel socket, ByteBuffer byteBuffer) throws IOException {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);

        byte[] bytes;
        if (buffers.containsKey(wrapper)) {
            bytes = buffers.get(wrapper);
        } else {
            bytes = new byte[0];
            buffers.put(wrapper, bytes);
        }

        bytes = messageEncoderDecoder.merge(bytes, byteBuffer);
        Message<?> message = messageEncoderDecoder.decode(bytes);
        buffers.put(wrapper, message == null ? bytes : new byte[0]);
        return message;
    }

    public void deleteSocket(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        buffers.remove(wrapper);
    }
}
