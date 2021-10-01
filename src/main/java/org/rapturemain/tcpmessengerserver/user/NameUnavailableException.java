package org.rapturemain.tcpmessengerserver.user;

public class NameUnavailableException extends Exception {
    public NameUnavailableException() {
        super();
    }

    public NameUnavailableException(String name) {
        super("Name [" + name + "] is unavailable");
    }
}
