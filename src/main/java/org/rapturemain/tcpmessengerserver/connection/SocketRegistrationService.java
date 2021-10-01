package org.rapturemain.tcpmessengerserver.connection;

import org.rapturemain.tcpmessengerserver.user.NameUnavailableException;
import org.rapturemain.tcpmessengerserver.user.User;

import javax.naming.NameNotFoundException;
import java.net.Socket;

public interface SocketRegistrationService {
    void register(Socket socket, User user) throws SocketRegistrationException;

    void unregister(Socket socket);

    User getUserForSocket(Socket socket);

    boolean isRegistered(Socket socket);
}
