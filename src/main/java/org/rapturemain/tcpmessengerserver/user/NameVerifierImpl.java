package org.rapturemain.tcpmessengerserver.user;

import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class NameVerifierImpl extends Verifier<Name> implements NameVerifier {

    @Override
    public void registerName(Name name) throws NameUnavailableException {
        try {
            register(name);
        } catch (UniqueConstraintException e) {
            throw new NameUnavailableException(name.getName());
        }
    }

    @Override
    public void unregisterName(Name name) {
        unregister(name);
    }
}
