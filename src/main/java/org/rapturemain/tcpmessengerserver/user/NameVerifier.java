package org.rapturemain.tcpmessengerserver.user;

public interface NameVerifier {

    void registerName(Name name) throws NameUnavailableException;

    void unregisterName(Name name);
}
