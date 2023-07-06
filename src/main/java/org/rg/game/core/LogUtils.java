package org.rg.game.core;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

import org.rg.game.lottery.engine.PersistentStorage;

public interface LogUtils {
	//public final static LogUtils INSTANCE = new LogUtils.ToConsole();
	public final static LogUtils INSTANCE = retrieveConfiguredLogger();

	static LogUtils retrieveConfiguredLogger() {
		String loggerType = EnvironmentUtils.getVariable("logger.type", "console");
		loggerType = "window";
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
			if (reports == null || reports.length == 0) {
				System.err.println();
			} else {
				for (String report : reports) {
					System.err.println(report);
				}
			}
			if (exc.getMessage() != null) {
				System.err.println(exc.getMessage());
			}
			for (StackTraceElement stackTraceElement : exc.getStackTrace()) {
				System.err.println("\t" + stackTraceElement.toString());
			}
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
			try {
				if (reports == null || reports.length == 0) {
					writer.append("\n");
				} else {
					for (String report : reports) {
						writer.append(report + "\n");
					}
				}
				if (exc.getMessage() != null) {
					writer.append(exc.getMessage() + "\n");
				}
				for (StackTraceElement stackTraceElement : exc.getStackTrace()) {
					writer.append("\t" + stackTraceElement.toString());
				}
			} catch (Throwable innerExc) {
				Throwables.sneakyThrow(exc);
			}
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
			if (reports == null || reports.length == 0) {
				logger.severe("\n");
			} else {
				for (String report : reports) {
					logger.severe(report + "\n");
				}
			}
			if (exc.getMessage() != null) {
				logger.severe(exc.getMessage() + "\n");
			}
			for (StackTraceElement stackTraceElement : exc.getStackTrace()) {
				logger.severe("\t" + stackTraceElement.toString() + "\n");
			}
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
			private final static int maxRowSize = Integer.valueOf(EnvironmentUtils.getVariable("logger.window.max-row-size", "10000"));
			private final static String backgroundColor = EnvironmentUtils.getVariable("logger.window.background-color", "67,159,54");
			private final static String textColor = EnvironmentUtils.getVariable("logger.window.text-color", "253,195,17");

			static {
				com.formdev.flatlaf.FlatLightLaf.setup();
				JFrame.setDefaultLookAndFeelDecorated(true);
			}

			private WindowHandler() {
				//LogManager manager = LogManager.getLogManager();
				//String className = this.getClass().getName();
				//String level = manager.getProperty(className + ".level");
				//setLevel(level != null ? Level.parse(level) : Level.ALL);
				setLevel(Level.ALL);
				if (textArea == null) {						javax.swing.JFrame window = new javax.swing.JFrame(EnvironmentUtils.getVariable("lottery.application.name", "Event logger")) {
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
							trunkTextIfMaxRowSizeReached(textArea);
							window.validate();
						};
					};					javax.swing.text.DefaultCaret caret = (javax.swing.text.DefaultCaret)textArea.getCaret();
					caret.setUpdatePolicy(javax.swing.text.DefaultCaret.ALWAYS_UPDATE);
					textArea.setBorder(BorderFactory.createCompoundBorder(
							textArea.getBorder(),
					        BorderFactory.createEmptyBorder(10, 10, 10, 10)));
					List<Integer> rGBColor = Arrays.asList(backgroundColor.split(",")).stream().map(Integer::valueOf).collect(Collectors.toList());
					Color firstColor = new Color(rGBColor.get(0), rGBColor.get(1), rGBColor.get(2));
					textArea.setBackground(firstColor);
					rGBColor = Arrays.asList(textColor.split(",")).stream().map(Integer::valueOf).collect(Collectors.toList());
					Color secondColor = new Color(rGBColor.get(0), rGBColor.get(1), rGBColor.get(2));
					textArea.setForeground(secondColor);
					textArea.setFont(new Font(textArea.getFont().getName(), Font.BOLD, textArea.getFont().getSize() + 2));

					window.getRootPane().putClientProperty("JRootPane.titleBarBackground", secondColor);
					window.getRootPane().putClientProperty("JRootPane.titleBarForeground", firstColor);
					JScrollPane scrollPane = new javax.swing.JScrollPane(textArea);

					window.add(new javax.swing.JScrollPane(textArea));
					window.setVisible(true);
					window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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

			public void trunkTextIfMaxRowSizeReached(JTextArea txtWin) {
			    int numLinesToTrunk = txtWin.getLineCount() - maxRowSize;
			    if(numLinesToTrunk > 0) {
			        try {
			            int posOfLastLineToTrunk = txtWin.getLineEndOffset(numLinesToTrunk - 1);
			            txtWin.replaceRange("",0,posOfLastLineToTrunk);
			        }
			        catch (BadLocationException ex) {
			            ex.printStackTrace();
			        }
			    }
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
