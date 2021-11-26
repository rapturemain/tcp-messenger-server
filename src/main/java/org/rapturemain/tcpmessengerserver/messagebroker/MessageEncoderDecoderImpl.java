package org.rapturemain.tcpmessengerserver.messagebroker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rapturemain.tcpmessengermessageframework.message.MessageEncoderDecoder;
import org.rapturemain.tcpmessengermessageframework.message.messages.Message;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@Component
public class MessageEncoderDecoderImpl implements MessageEncoderDecoder {

    private final org.rapturemain.tcpmessengermessageframework.message.MessageEncoderDecoderImpl encoderDecoder =
            new org.rapturemain.tcpmessengermessageframework.message.MessageEncoderDecoderImpl();

    @PostConstruct
    public void start() {
        encoderDecoder.start();
    }

    @Override
    public void encode(@NotNull Message<?> message, @NotNull DataOutputStream dataOutputStream) throws IOException {
        encoderDecoder.encode(message, dataOutputStream);
    }

    @Override
    public @Nullable Message<?> decode(@NotNull DataInputStream dataInputStream) throws IOException {
        return encoderDecoder.decode(dataInputStream);
    }

    @Override
    public @Nullable Message<?> decode(byte[] bytes) throws IOException {
        return encoderDecoder.decode(bytes);
    }

    @Override
    public boolean canBeDecoded(byte[] bytes, @NotNull ByteBuffer byteBuffer) {
        return encoderDecoder.canBeDecoded(bytes, byteBuffer);
    }

    @Override
    public byte[] merge(byte[] bytes, @NotNull ByteBuffer byteBuffer) {
        return encoderDecoder.merge(bytes, byteBuffer);
    }
}
