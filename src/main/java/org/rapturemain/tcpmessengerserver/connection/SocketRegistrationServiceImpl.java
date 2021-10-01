package org.rapturemain.tcpmessengerserver.connection;

import org.rapturemain.tcpmessengerserver.user.NameUnavailableException;
import org.rapturemain.tcpmessengerserver.user.User;
import org.rapturemain.tcpmessengerserver.user.UserInfoVerifier;
import org.rapturemain.tcpmessengerserver.utils.DefaultWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Socket;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocketRegistrationServiceImpl implements SocketRegistrationService {

    private final UserInfoVerifier userInfoVerifier;

    private final ConcurrentHashMap<DefaultWrapper<Socket>, User> sockets = new ConcurrentHashMap<>();

    @Autowired
    public SocketRegistrationServiceImpl(UserInfoVerifier userInfoVerifier) {
        this.userInfoVerifier = userInfoVerifier;
    }

    @Override
    public void register(Socket socket, User user) throws SocketRegistrationException {
        DefaultWrapper<Socket> wrapper = new DefaultWrapper<>(socket);

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

    @Override
    public void unregister(Socket socket) {
        DefaultWrapper<Socket> wrapper = new DefaultWrapper<>(socket);
        if (!sockets.containsKey(wrapper)) {
            return;
        }
        userInfoVerifier.unregisterUser(sockets.get(wrapper));
        sockets.remove(wrapper);
    }

    @Override
    public User getUserForSocket(Socket socket) {
        DefaultWrapper<Socket> wrapper = new DefaultWrapper<>(socket);
        if (!sockets.containsKey(wrapper)) {
            throw new NoSuchElementException();
        }
        return sockets.get(wrapper);
    }

    @Override
    public boolean isRegistered(Socket socket) {
        DefaultWrapper<Socket> wrapper = new DefaultWrapper<>(socket);
        return sockets.containsKey(wrapper);
    }
}
