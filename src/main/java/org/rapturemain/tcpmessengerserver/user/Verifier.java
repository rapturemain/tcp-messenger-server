package org.rapturemain.tcpmessengerserver.user;

import java.util.concurrent.ConcurrentHashMap;

public abstract class Verifier<T> {
    private static final Object dummy = new Object();

    private final ConcurrentHashMap<T, Object> uniques = new ConcurrentHashMap<>();

    protected void register(T unique) throws UniqueConstraintException {
        Object o = uniques.put(unique, dummy);
        if (o != null) {
            throw new UniqueConstraintException();
        }
    }

    protected void unregister(T unique) {
        uniques.remove(unique);
    }
}
