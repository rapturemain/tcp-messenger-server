package org.rapturemain.tcpmessengerserver.utils;

import lombok.AllArgsConstructor;
import lombok.Value;


@Value
@AllArgsConstructor
public class IdentityWrapper<T> {

    T data;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentityWrapper<?> that = (IdentityWrapper<?>) o;
        return data == that.data;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(data);
    }
}
