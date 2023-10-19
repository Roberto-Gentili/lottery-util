package org.rg.game.core;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable> {

	T get() throws E;

}
