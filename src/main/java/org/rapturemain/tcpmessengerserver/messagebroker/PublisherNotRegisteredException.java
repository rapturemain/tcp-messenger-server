package org.rapturemain.tcpmessengerserver.messagebroker;

public class PublisherNotRegisteredException extends RuntimeException {
    public PublisherNotRegisteredException() {
        super();
    }

    public PublisherNotRegisteredException(String message) {
        super(message);
    }
}
