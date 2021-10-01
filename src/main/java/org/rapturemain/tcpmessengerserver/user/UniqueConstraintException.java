package org.rapturemain.tcpmessengerserver.user;

public class UniqueConstraintException extends Exception {

    public UniqueConstraintException() {
        super();
    }

    public UniqueConstraintException(String value) {
        super(value);
    }
}
