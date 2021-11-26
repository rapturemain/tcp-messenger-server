package org.rapturemain.tcpmessengerserver.connection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageWrapper {
    ByteBuffer message;
    boolean handleExceptions;
}
