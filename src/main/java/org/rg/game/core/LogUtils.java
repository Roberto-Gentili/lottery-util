package org.rg.game.core;

public class LogUtils {

	public static void info(String... reports) {
		for (String report : reports) {
			LogUtils.info(report);
		}
	}

	public static void error(String... reports) {
		for (String report : reports) {
			System.err.println(report);
		}
	}

}
