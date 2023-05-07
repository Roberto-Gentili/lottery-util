package org.rg.game.core;

public class Throwables {

	public static <E extends Throwable, R> R sneakyThrow(Throwable e) throws E {
	    throw (E) e;
	}

}
