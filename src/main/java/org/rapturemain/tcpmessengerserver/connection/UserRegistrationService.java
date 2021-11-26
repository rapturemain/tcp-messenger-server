package org.rapturemain.tcpmessengerserver.connection;

import org.rapturemain.tcpmessengerserver.user.NameUnavailableException;
import org.rapturemain.tcpmessengerserver.user.User;
import org.rapturemain.tcpmessengerserver.user.UserInfoVerifier;
import org.rapturemain.tcpmessengerserver.utils.IdentityWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.channels.SocketChannel;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserRegistrationService {

    private final UserInfoVerifier userInfoVerifier;
    private final ConcurrentHashMap<IdentityWrapper<SocketChannel>, User> sockets = new ConcurrentHashMap<>();

    @Autowired
    public UserRegistrationService(UserInfoVerifier userInfoVerifier) {
        this.userInfoVerifier = userInfoVerifier;
    }

    public void register(SocketChannel socket, User user) throws SocketRegistrationException {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);

        try {
            userInfoVerifier.registerUser(user);
        } catch (NameUnavailableException e) {
            throw new SocketRegistrationException();
        }

        User oldUser = sockets.put(wrapper, user);

        if (oldUser != null) {
            userInfoVerifier.unregisterUser(oldUser);
        }
    }

    public void unregister(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        if (!sockets.containsKey(wrapper)) {
            return;
        }
        userInfoVerifier.unregisterUser(sockets.get(wrapper));
        sockets.remove(wrapper);
    }

    public User getUserForSocket(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        if (!sockets.containsKey(wrapper)) {
            throw new NoSuchElementException();
        }
        return sockets.get(wrapper);
    }

    public boolean isRegistered(SocketChannel socket) {
        IdentityWrapper<SocketChannel> wrapper = new IdentityWrapper<>(socket);
        return sockets.containsKey(wrapper);
    }
}
