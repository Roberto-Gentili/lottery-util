package org.rg.game.core;

import java.io.PrintStream;

public class LogUtils {
	public final static LogUtils INSTANCE = new LogUtils();


	public void debug(String... reports) {
		log(System.out, reports);
	}

	public void info(String... reports) {
		log(System.out, reports);
	}

	public void warn(String... reports) {
		log(System.err, reports);
	}

	public void error(String... reports) {
		log(System.err, reports);
	}

	private void log(PrintStream stream, String... reports) {
		if (reports == null || reports.length == 0) {
			stream.println();
			return;
		}
		for (String report : reports) {
			stream.println(report);
		}
	}

	public static class ToFile extends LogUtils {

		@Override
		public void debug(String... reports) {

		}

		@Override
		public void info(String... reports) {

		}

		@Override
		public void warn(String... reports) {

		}

		@Override
		public void error(String... reports) {

		}

	}

}
