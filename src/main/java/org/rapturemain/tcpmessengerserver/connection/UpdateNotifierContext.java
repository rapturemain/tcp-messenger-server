package org.rapturemain.tcpmessengerserver.connection;

import lombok.Builder;
import lombok.Value;
import org.rapturemain.tcpmessengerserver.messagebroker.MessageSubscriber;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@Builder
public class UpdateNotifierContext {
    MessageSubscriber subscriber;
    InputStream socketInputStream;
    AtomicBoolean isWorking;
}
