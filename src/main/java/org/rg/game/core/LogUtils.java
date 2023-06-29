package org.rg.game.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.rg.game.lottery.engine.PersistentStorage;

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
		public final static Map<String, ToFile> INSTANCES = new ConcurrentHashMap<>();
		private BufferedWriter writer;

		public ToFile(String absolutePath) {
			try {
				try (FileChannel outChan = new FileOutputStream(absolutePath, true).getChannel()) {
				  //outChan.truncate(0);
				} catch (IOException exc) {
					Throwables.sneakyThrow(exc);
				}
				writer = new BufferedWriter(new FileWriter(absolutePath, true));
			} catch (IOException exc) {
				Throwables.sneakyThrow(exc);
			}
		}

		public final static LogUtils getLogger(String relativePath) {
			String absolutePath =
				PersistentStorage.buildWorkingPath() + File.separator + (relativePath = relativePath != null? relativePath : "log.txt");
			return INSTANCES.computeIfAbsent(relativePath, key -> new ToFile(absolutePath));
		}

		@Override
		public void debug(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[DEBUG] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void info(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[INFO] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void warn(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[WARN] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void error(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[ERROR] - " + reports[i];
			}
			log(reports);
		}

		private void log(String... reports) {
			try {
				if (reports == null || reports.length == 0) {
					writer.append("\n");
					return;
				}
				for (String report : reports) {
					writer.append(report + "\n");
				}
				writer.flush();
			} catch (Throwable exc) {
				Throwables.sneakyThrow(exc);
			}
		}




	}

}
