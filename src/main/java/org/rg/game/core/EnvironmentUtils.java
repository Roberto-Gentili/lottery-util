package org.rg.game.core;

public class EnvironmentUtils {
	public static final EnvironmentUtils INSTANCE = new EnvironmentUtils();


	public String getVariable(String key, String defaultValue) {
		String value = System.getenv(key);
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

}
