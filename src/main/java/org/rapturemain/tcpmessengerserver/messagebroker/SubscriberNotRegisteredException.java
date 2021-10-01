package org.rapturemain.tcpmessengerserver.messagebroker;

public class SubscriberNotRegisteredException extends RuntimeException {
    public SubscriberNotRegisteredException() {
        super();
    }

    public SubscriberNotRegisteredException(String message) {
        super(message);
    }
}
