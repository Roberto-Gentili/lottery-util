package org.rg.game.core;

import java.io.PrintStream;

public class LogUtils {

	public static void info(String... reports) {
		log(System.out, reports);
	}

	public static void warn(String... reports) {
		log(System.err, reports);
	}

	public static void error(String... reports) {
		log(System.err, reports);
	}

	private static synchronized void log(PrintStream stream, String... reports) {
		if (reports == null || reports.length == 0) {
			stream.println();
			return;
		}
		for (String report : reports) {
			stream.println(report);
		}
	}

}
