package org.rapturemain.tcpmessengerserver.user;

import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class UserInfoVerifierImpl extends Verifier<User> implements UserInfoVerifier {

    private static final User SERVER = User.builder()
            .name(new Name("SERVER"))
            .build();

    private final NameVerifier nameVerifier;

    @Autowired
    public UserInfoVerifierImpl(NameVerifier nameVerifier) {
        this.nameVerifier = nameVerifier;
    }

    @PostConstruct
    @SneakyThrows
    public void start() {
        registerUser(SERVER);
    }

    @Override
    public void registerUser(User user) throws NameUnavailableException {
        nameVerifier.registerName(user.getName());
    }

    @Override
    public void unregisterUser(User user) {
        nameVerifier.unregisterName(user.getName());
    }
}
