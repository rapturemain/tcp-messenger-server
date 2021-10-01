package org.rapturemain.tcpmessengerserver.connection;

import java.net.Socket;

public interface ConnectionHandler {
    void handleConnection(Socket socket);
}
