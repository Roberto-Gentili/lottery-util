package org.rg.game.core;

import java.util.Objects;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {

    void accept(T t) throws E;

    default ThrowingConsumer<T, E> andThen(ThrowingConsumer<? super T, ? extends E> after) {
        Objects.requireNonNull(after);
        return (T t) -> { accept(t); after.accept(t); };
    }

}

