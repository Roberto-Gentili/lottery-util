package org.rg.game.core;

import java.util.Objects;

@FunctionalInterface
public interface ThrowingRunnable<E extends Throwable> {

    public abstract void run() throws E;

    default ThrowingRunnable<E> andThen(ThrowingRunnable<E> after) {
        Objects.requireNonNull(after);
        return () -> {
        	run();
        	after.run();
        };
    }
}