package org.rg.game.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.rg.game.lottery.engine.PersistentStorage;

public interface LogUtils {
	//public final static LogUtils INSTANCE = new LogUtils.ToConsole();
	public final static LogUtils INSTANCE = retrieveConfiguredLogger();

	static LogUtils retrieveConfiguredLogger() {
		String loggerType = EnvironmentUtils.getVariable("logger.type", "console");
		if (loggerType.equalsIgnoreCase("console")) {
			return new LogUtils.ToConsole();
		} else if (loggerType.equalsIgnoreCase("file")) {
			return LogUtils.ToFile.getLogger("default-log.txt");
		} else if (loggerType.equalsIgnoreCase("window")) {
			return new LogUtils.ToWindow();
		}
		throw new IllegalArgumentException(loggerType + " is not a valid logger type");
	}


	public void debug(String... reports);

	public void info(String... reports);

	public void warn(String... reports);

	public void error(String... reports);

	public void error(Throwable exc, String... reports);

	public static class ToConsole implements LogUtils {

		@Override
		public void debug(String... reports) {
			log(System.out, reports);
		}

		@Override
		public void info(String... reports) {
			log(System.out, reports);
		}

		@Override
		public void warn(String... reports) {
			log(System.err, reports);
		}

		@Override
		public void error(String... reports) {
			log(System.err, reports);
		}

		@Override
		public void error(Throwable exc, String... reports) {
			exc.printStackTrace();
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
	}

	public static class ToFile implements LogUtils {
		public final static Map<String, ToFile> INSTANCES = new ConcurrentHashMap<>();
		private BufferedWriter writer;

		private ToFile(String absolutePath) {
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

		@Override
		public void error(Throwable exc, String... reports) {
			throw new UnsupportedOperationException();
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

	public static class ToWindow implements LogUtils {
		private Logger logger;

		ToWindow() {
			logger = WindowHandler.attachNewWindowToLogger("logging.handler");
		}

		@Override
		public void debug(String... reports) {
			log(logger::fine, reports);
		}

		@Override
		public void info(String... reports) {
			log(logger::info, reports);
		}

		@Override
		public void warn(String... reports) {
			log(logger::warning, reports);
		}

		@Override
		public void error(String... reports) {
			log(logger::severe, reports);
		}

		@Override
		public void error(Throwable exc, String... reports) {
			exc.printStackTrace();
		}

		private void log(Consumer<String> logger, String... reports) {
			if (reports == null || reports.length == 0) {
				logger.accept("\n");
				return;
			}
			for (String report : reports) {
				logger.accept(report + "\n");
			}
		}


		private static class WindowHandler extends Handler {
			private javax.swing.JTextArea textArea;

			private WindowHandler() {
				//LogManager manager = LogManager.getLogManager();
				//String className = this.getClass().getName();
				//String level = manager.getProperty(className + ".level");
				//setLevel(level != null ? Level.parse(level) : Level.ALL);
				setLevel(Level.ALL);
				if (textArea == null) {						javax.swing.JFrame window = new javax.swing.JFrame(Optional.ofNullable(System.getenv("lottery.application.name")).orElseGet(() -> "Event logger")) {
						private static final long serialVersionUID = 653831741693111851L;
						{
							setSize(800, 600);
						}
					};
					textArea = new javax.swing.JTextArea() {
						private static final long serialVersionUID = -5669120951831828004L;

						@Override
						public void append(String value) {
							super.append(value);
							window.validate();
						};
					};					javax.swing.text.DefaultCaret caret = (javax.swing.text.DefaultCaret)textArea.getCaret();
					caret.setUpdatePolicy(javax.swing.text.DefaultCaret.ALWAYS_UPDATE);
					window.add(new javax.swing.JScrollPane(textArea));
					window.setVisible(true);
				}
				//setFormatter(new SimpleFormatter());
				setFormatter(new Formatter() {

					@Override
					public String format(LogRecord record) {
						return record.getMessage();
					}
				});
			}

			public static Logger attachNewWindowToLogger(String loggerName) {
				WindowHandler WindowHandler = new WindowHandler();
				Logger logger = Logger.getLogger(loggerName);
				logger.addHandler(WindowHandler);
				return logger;
			}

			@Override
			public synchronized void publish(LogRecord record) {
				String message = null;
				if (!isLoggable(record)) {
					return;
				}
				message = getFormatter().format(record);
				textArea.append(message);
			}

			@Override
			public void close() {}

			@Override
			public void flush() {}

		}

	}

}
