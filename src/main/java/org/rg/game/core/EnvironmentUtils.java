package org.rg.game.core;

public class EnvironmentUtils {

	public static String getVariable(String key, String defaultValue) {
		String value = System.getenv(key);
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

}
