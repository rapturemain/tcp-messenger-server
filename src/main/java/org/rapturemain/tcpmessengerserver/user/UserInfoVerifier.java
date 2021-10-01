package org.rapturemain.tcpmessengerserver.user;

public interface UserInfoVerifier {
    void registerUser(User user) throws NameUnavailableException;

    void unregisterUser(User user);
}
