package org.rapturemain.tcpmessengerserver.connection;

import lombok.extern.slf4j.Slf4j;
import org.rapturemain.tcpmessengerserver.utils.IdentityWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.channels.SocketChannel;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

@Slf4j
@Component
public class SocketRegistrationService {

    private final ConcurrentSkipListSet<IdentityWrapper<SocketChannel>> sockets =
            new ConcurrentSkipListSet<>(Comparator.comparingInt(IdentityWrapper::hashCode));

    public void register(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        sockets.add(wrapper);
    }

    public void unregister(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        sockets.remove(wrapper);
    }

    public boolean isRegistered(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        return sockets.contains(wrapper);
    }

    public void executeForEach(Consumer<SocketChannel> action) {
        sockets.forEach(wrapper -> action.accept(wrapper.getData()));
    }
}
