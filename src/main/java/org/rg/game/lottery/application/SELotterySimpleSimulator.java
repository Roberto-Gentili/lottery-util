package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

import org.apache.poi.EmptyFileException;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.PartAlreadyExistsException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.impl.values.XmlValueDisconnectedException;
import org.rg.game.core.CollectionUtils;
import org.rg.game.core.ConcurrentUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.NetworkUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.Synchronizer;
import org.rg.game.core.Synchronizer.Mutex;
import org.rg.game.core.Throwables;
import org.rg.game.core.ThrowingConsumer;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;
import org.rg.game.lottery.engine.Storage;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;



public class SELotterySimpleSimulator {
	static final Map<String, Integer> savingOperationCounters = new ConcurrentHashMap<>();
	static final String SALDO_LABEL = "Saldo";
	static final String RITORNO_LABEL = "Ritorno";
	static final String COSTO_LABEL = "Costo";
	static final String DATA_LABEL = "Data";
	static final String STORICO_SUFFIX = "(storico)";
	static final String SALDO_STORICO_LABEL = "Saldo " + STORICO_SUFFIX;
	static final String RITORNO_STORICO_LABEL = "Ritorno " + STORICO_SUFFIX;
	static final String COSTO_STORICO_LABEL = "Costo " + STORICO_SUFFIX;
	static final String STORICO_PROGRESSIVO_ANTERIORE_SUFFIX = "(st. prg. ant.)";
	static final String SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL = "Saldo " + STORICO_PROGRESSIVO_ANTERIORE_SUFFIX;
	static final String RITORNO_STORICO_PROGRESSIVO_ANTERIORE_LABEL = "Ritorno " + STORICO_PROGRESSIVO_ANTERIORE_SUFFIX;
	static final String COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL = "Costo " + STORICO_PROGRESSIVO_ANTERIORE_SUFFIX;
	static final String FILE_LABEL = "File";
	static final String DATA_AGGIORNAMENTO_STORICO_LABEL = "Data agg. storico";
	static final List<String> excelHeaderLabels;

	static final ObjectMapper objectMapper = new ObjectMapper();
	static Pattern regexForExtractConfigFileName = Pattern.compile("\\[.*?\\]\\[.*?\\]\\[.*?\\](.*)\\.txt");
	static String hostName;
	static SEStats allTimeStats;

	static {
		ZipSecureFile.setMinInflateRatio(0);
		hostName = NetworkUtils.INSTANCE.thisHostName();
		excelHeaderLabels = new ArrayList<>();
		excelHeaderLabels.add(DATA_LABEL);
		Collection<String> allPremiumLabels = Premium.allLabels();
		excelHeaderLabels.addAll(allPremiumLabels);
		excelHeaderLabels.add(COSTO_LABEL);
		excelHeaderLabels.add(RITORNO_LABEL);
		excelHeaderLabels.add(SALDO_LABEL);
		List<String> historyLabels = allPremiumLabels.stream().map(label -> getFollowingProgressiveHistoryPremiumLabel(label)).collect(Collectors.toList());
		excelHeaderLabels.addAll(historyLabels);
		excelHeaderLabels.add(COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL);
		excelHeaderLabels.add(RITORNO_STORICO_PROGRESSIVO_ANTERIORE_LABEL);
		excelHeaderLabels.add(SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL);
		historyLabels = allPremiumLabels.stream().map(label -> getHistoryPremiumLabel(label)).collect(Collectors.toList());
		excelHeaderLabels.addAll(historyLabels);
		excelHeaderLabels.add(COSTO_STORICO_LABEL);
		excelHeaderLabels.add(RITORNO_STORICO_LABEL);
		excelHeaderLabels.add(SALDO_STORICO_LABEL);
		excelHeaderLabels.add(DATA_AGGIORNAMENTO_STORICO_LABEL);
		excelHeaderLabels.add(FILE_LABEL);
	}

	public static void main(String[] args) throws IOException {
		Collection<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
		executeRecursive(SELotterySimpleSimulator::execute, futures);
	}

	protected static void executeRecursive(
		BiFunction<
			String,
			Collection<CompletableFuture<Void>>,
			Collection<CompletableFuture<Void>>
		> executor,
		Collection<CompletableFuture<Void>> futures
	) {
		try {
			executor.apply("se", futures).stream().forEach(CompletableFuture::join);
		} catch (Throwable exc) {
			exc.printStackTrace();
			executeRecursive(executor, futures);
		}
	}

	protected static Collection<CompletableFuture<Void>> execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) {
		SEStats.forceLoadingFromExcel = false;
		allTimeStats = SEStats.get("03/12/1997", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.get("02/07/2009", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		Supplier<SELotteryMatrixGeneratorEngine> engineSupplier = SELotteryMatrixGeneratorEngine::new;
		String[] configurationFileFolders = Shared.pathsFromSystemEnv(
			"working-path.simulations.folder",
			"resources.simulations.folder"
		);
		LogUtils.info("Set configuration files folder to " + String.join(", ", configurationFileFolders) + "\n");
		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-simple-simulation", "properties",
				configurationFileFolders
			);
		try {
			prepareAndProcess(
				futures,
				engineSupplier,
				toConfigurations(
					configurationFiles,
					"simulation.slave"
				)
			);
		} catch (IOException exc) {
			Throwables.sneakyThrow(exc);
		}
		return futures;
	}

	protected static List<Properties> toConfigurations(List<File> configurationFiles, String key) throws IOException {
		String forceSlave = System.getenv("forceSlave");
		String forceMaster = System.getenv("forceMaster");
		List<Properties> configurations = ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles);
		if (forceMaster != null && Boolean.valueOf(forceMaster.replaceAll("\\s+",""))) {
			configurations.stream().forEach(config -> config.setProperty(key, "false"));
		}
		if (forceSlave != null && Boolean.valueOf(forceSlave.replaceAll("\\s+",""))) {
			configurations.stream().forEach(config -> config.setProperty(key, "true"));
		}
		return configurations;
	}



	protected static void prepareAndProcess(
		Collection<CompletableFuture<Void>> futures,
		Supplier<SELotteryMatrixGeneratorEngine> engineSupplier,
		List<Properties> configurationProperties
	) throws IOException {
		List<Properties> configurations = new ArrayList<>();
		for (Properties config : configurationProperties) {
			String simulationDates = config.getProperty("simulation.dates");
			if (CollectionUtils.retrieveBoolean(config, "simulation.enabled", "false")) {
				if (simulationDates != null) {
					config.setProperty("competition", simulationDates);
				}
				setGroup(config);
				config.setProperty("storage", "filesystem");
				config.setProperty("overwrite-if-exists", String.valueOf(CollectionUtils.retrieveBoolean(config, "simulation.slave", "false")? -1 : 0));
				configurations.add(config);
			}
		}

		for (Properties configuration : configurations) {
			LogUtils.info(
				"Processing file '" + configuration.getProperty("file.name") + "' located in '" + configuration.getProperty("file.parent.absolutePath") + "' in " +
					(CollectionUtils.retrieveBoolean(configuration, "simulation.slave") ? "slave" : "master") + " mode"
			);
			String info = configuration.getProperty("info");
			if (info != null) {
				LogUtils.info(info);
			}
			String excelFileName = retrieveExcelFileName(configuration, "simulation.group");
			SELotteryMatrixGeneratorEngine engine = engineSupplier.get();
			String configFileName = configuration.getProperty("file.name").replace("." + configuration.getProperty("file.extension"), "");
			configuration.setProperty(
				"nameSuffix",
				configFileName
			);
			Collection<LocalDate> competitionDatesFlat = engine.computeExtractionDates(configuration.getProperty("competition"));
			String redundantConfigValue = configuration.getProperty("simulation.redundancy");
			cleanup(
				configuration,
				excelFileName,
				competitionDatesFlat,
				configFileName,
				redundantConfigValue != null? Integer.valueOf(redundantConfigValue) : null
			);
			List<List<LocalDate>> competitionDates =
				CollectionUtils.toSubLists(
					new ArrayList<>(competitionDatesFlat),
					redundantConfigValue != null? Integer.valueOf(redundantConfigValue) : 10
				);
			configuration.setProperty("report.enabled", "true");
			Runnable taskOperation = () -> {
				LogUtils.info("Computation of " + configuration.getProperty("file.name") + " started");
				process(configuration, excelFileName, engine, competitionDates);
				LogUtils.info("Computation of " + configuration.getProperty("file.name") + " succesfully finished");
			};
			if (CollectionUtils.retrieveBoolean(configuration, "async", "false")) {
				ConcurrentUtils.addTask(futures, taskOperation);
			} else {
				taskOperation.run();
			}
			ConcurrentUtils.waitUntil(futures, ft -> ft.size() >= 5);
		}
	}

	protected static String retrieveExcelFileName(Properties configuration, String groupKey) {
		return Optional.ofNullable(configuration.getProperty(groupKey)).map(groupName -> {
			PersistentStorage.buildWorkingPath(groupName);
			String reportFileName = (groupName.contains("\\") ?
				groupName.substring(groupName.lastIndexOf("\\") + 1) :
					groupName.contains("/") ?
					groupName.substring(groupName.lastIndexOf("/") + 1) :
						groupName) + "-report.xlsx";
			return groupName + File.separator + reportFileName;
		}).orElseGet(() -> configuration.getProperty("file.name").replace("." + configuration.getProperty("file.extension"), "") + "-sim.xlsx");
	}

	protected static void setGroup(Properties config) {
		String simulationGroup = config.getProperty("simulation.group");
		if (simulationGroup != null) {
			simulationGroup = simulationGroup.replace("${localhost.name}", hostName);
			config.setProperty("simulation.group", simulationGroup);
			config.setProperty(
				"group",
				simulationGroup
			);
		}
	}

	protected static LocalDate removeNextOfLatestExtractionDate(Properties config, Collection<LocalDate> extractionDates) {
		Iterator<LocalDate> extractionDatesIterator = extractionDates.iterator();
		LocalDate latestExtractionArchiveStartDate = TimeUtils.toLocalDate(getSEStats(config).getLatestExtractionDate());
		LocalDate nextAfterLatest = null;
		while (extractionDatesIterator.hasNext()) {
			LocalDate currentIterated = extractionDatesIterator.next();
			if (currentIterated.compareTo(latestExtractionArchiveStartDate) > 0) {
				if (nextAfterLatest == null) {
					nextAfterLatest = currentIterated;
				}
				extractionDatesIterator.remove();
			}
		}
		return nextAfterLatest;
	}

	protected static void cleanup(
		Properties configuration,
		String excelFileName,
		Collection<LocalDate> competitionDates,
		String configFileName, Integer redundancy
	) {
		removeNextOfLatestExtractionDate(configuration, competitionDates);
		int initialSize = competitionDates.size();
		if (redundancy != null) {
			cleanupRedundant(
				configuration,
				excelFileName, configFileName, redundancy, competitionDates
			);
		}
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		readOrCreateExcel(
			excelFileName,
			workBook -> {
				Iterator<Row> rowIterator = workBook.getSheet("Risultati").rowIterator();
				rowIterator.next();
				rowIterator.next();
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					Cell date = row.getCell(0);
					if (rowRefersTo(row, configFileName)) {
						Iterator<LocalDate> competitionDatesItr = competitionDates.iterator();
						while (competitionDatesItr.hasNext()) {
							LocalDate competitionDate = competitionDatesItr.next();
							if (date != null && competitionDate.compareTo(TimeUtils.toLocalDate(date.getDateCellValue())) == 0) {
								competitionDatesItr.remove();
							}
						}
					}
				}
			},
			workBook ->
				createWorkbook(workBook, excelFileName),
			workBook ->
				store(excelFileName, workBook, isSlave),
			isSlave
		);
		LogUtils.info(competitionDates.size() + " dates will be processed, " + (initialSize - competitionDates.size()) + " already processed for file " + configuration.getProperty("file.name"));
	}

	private static void cleanupRedundant(Properties configuration, String excelFileName, String configFileName, Integer redundancy, Collection<LocalDate> competitionDatesFlat) {
		List<LocalDate> competionDateLatestBlock =
			CollectionUtils.toSubLists(
				new ArrayList<>(competitionDatesFlat),
				redundancy
			).stream().reduce((prev, next) -> next).orElse(null);
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		readOrCreateExcel(
			excelFileName,
			workBook -> {
				Sheet sheet = workBook.getSheet("Risultati");
				Iterator<Row> rowIterator = sheet.rowIterator();
				rowIterator.next();
				rowIterator.next();
				Map<String, List<Row>> groupedForRedundancyRows = new LinkedHashMap<>();
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					if (rowRefersTo(row, configFileName)) {
						groupedForRedundancyRows.computeIfAbsent(
							row.getCell(row.getLastCellNum()-1).getStringCellValue(),
							key -> new ArrayList<>()
						).add(row);
					}
				}
				List<List<Row>> allGroupedForRedundancyRows = new ArrayList<>(groupedForRedundancyRows.values());
				List<Row> latestGroupOfRows = allGroupedForRedundancyRows.stream().reduce((prev, next) -> next).orElse(null);
				allGroupedForRedundancyRows.remove(latestGroupOfRows);
				for (List<Row> rows : allGroupedForRedundancyRows) {
					if (rows.size() < redundancy) {
						for (Row row : rows) {
							sheet.removeRow(row);
						}
					}
				}
				if (latestGroupOfRows != null && latestGroupOfRows.size() != redundancy && latestGroupOfRows.size() != competionDateLatestBlock.size()) {
					for (Row row : latestGroupOfRows) {
						sheet.removeRow(row);
					}
				}
			},
			workBook ->
				createWorkbook(workBook, excelFileName),
			workBook ->
				store(excelFileName, workBook, isSlave),
			isSlave
		);
	}

	private static boolean rowRefersTo(Row row, String configurationName) {
		Matcher matcher = regexForExtractConfigFileName.matcher(
			row.getCell(Shared.getCellIndex(row.getSheet(), FILE_LABEL)).getStringCellValue()
		);
		return matcher.find() && matcher.group(1).equals(configurationName);
	}

	protected static void process(
		Properties configuration,
		String excelFileName,
		SELotteryMatrixGeneratorEngine engine,
		List<List<LocalDate>> competitionDates
	) {
		String redundantConfigValue = configuration.getProperty("simulation.redundancy");
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		Function<LocalDate, Function<List<Storage>, Integer>> extractionDatePredicate = null;
		Function<LocalDate, Consumer<List<Storage>>> systemProcessor = null;
		AtomicInteger redundantCounter = new AtomicInteger(0);
		if (isSlave) {
			if (redundantConfigValue != null) {
				for (
					List<LocalDate> datesToBeProcessed :
					competitionDates
				) {
					LocalDate date = datesToBeProcessed.get(0);
					datesToBeProcessed.clear();
					datesToBeProcessed.add(date);
				}
			}
			Collections.shuffle(competitionDates);
		} else {
			extractionDatePredicate = buildExtractionDatePredicate(
				configuration,
				excelFileName,
				redundantConfigValue != null? Integer.valueOf(redundantConfigValue) : null,
				redundantCounter
			);
			systemProcessor = buildSystemProcessor(configuration, excelFileName);
		}
		AtomicBoolean simulatorFinished = new AtomicBoolean(false);
		AtomicBoolean historyUpdateTaskStarted = new AtomicBoolean(false);
		CompletableFuture<Void> historyUpdateTask = startHistoryUpdateTask(
			configuration, excelFileName, configuration.getProperty("nameSuffix"), historyUpdateTaskStarted, simulatorFinished
		);

		for (
			List<LocalDate> datesToBeProcessed :
			competitionDates
		) {
			configuration.setProperty("competition",
				String.join(",",
					datesToBeProcessed.stream().map(TimeUtils.defaultLocalDateFormat::format).collect(Collectors.toList())
				)
			);
			engine.setup(configuration);
			engine.getExecutor().apply(
				extractionDatePredicate
			).apply(
				systemProcessor
			);
		}
		while (!historyUpdateTaskStarted.get()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException exc) {
				Throwables.sneakyThrow(exc);
			}
		}
		simulatorFinished.set(true);
		historyUpdateTask.join();
		if (!isSlave) {
			readOrCreateExcel(
				excelFileName,
				workBook -> {
					SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
					workBookTemplate.getOrCreateSheet("Risultati", true);
					workBookTemplate.setAutoFilter(1, getLatestRowIndex(), 0, excelHeaderLabels.size() - 1);
				},
				null,
				workBook -> {
					store(excelFileName, workBook, isSlave);
				},
				CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false")
			);
		}
		backup(new File(PersistentStorage.buildWorkingPath() + File.separator + excelFileName), isSlave);
		//Puliamo file txt duplicati da google drive
		for (File file : ResourceUtils.INSTANCE.find("(1)", "txt", retrieveBasePath(configuration))) {
			file.delete();
		}
	}

	private static int getLatestRowIndex() {
		return SpreadsheetVersion.EXCEL2007.getMaxRows() -1;
		//return Shared.getSEStats().getAllWinningCombos().size() * 2;
	}

	private static CompletableFuture<Void> startHistoryUpdateTask(
		Properties configuration,
		String excelFileName,
		String configurationName,
		AtomicBoolean historyUpdateTaskStarted,
		AtomicBoolean simulatorFinished
	) {
		return CompletableFuture.runAsync(() -> {
			boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
			int initialPriority = Thread.currentThread().getPriority();
			if (!isSlave) {
				if (!simulatorFinished.get()) {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				} else{
					setThreadPriorityToMax();
				}
			}
			Map<String, Map<String, Object>> premiumCountersForFile = new LinkedHashMap<>();
			Integer updateHistoryResult = null;
			while ((!simulatorFinished.get()) && (updateHistoryResult == null || updateHistoryResult.compareTo(-1) != 0)) {
				historyUpdateTaskStarted.set(true);
				updateHistoryResult = updateHistory(configuration, excelFileName, configurationName, premiumCountersForFile, simulatorFinished);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException exc) {
					Throwables.sneakyThrow(exc);
				}
			}
			setThreadPriorityToMax();
			if (updateHistoryResult == null || updateHistoryResult.compareTo(-1) != 0) {
				updateHistory(configuration, excelFileName, configurationName, premiumCountersForFile, simulatorFinished);
			}
			Thread.currentThread().setPriority(initialPriority);
		});

	}

	private static void setThreadPriorityToMax() {
		if (Thread.currentThread().getPriority() != Thread.MAX_PRIORITY) {
			LogUtils.info("Setting priority of Thread " + Thread.currentThread() + " to max");
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		}
	}

	private static Integer updateHistory(
		Properties configuration,
		String excelFileName,
		String configurationName,
		Map<String, Map<String, Object>> premiumCountersForFile,
		AtomicBoolean simulatorFinished
	) {
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		SEStats sEStats = getSEStats(configuration);
		Map<Integer, String> allPremiums = Premium.all();
		AtomicInteger dataColIndex = new AtomicInteger(-1);
		AtomicInteger dataAggStoricoColIndex = new AtomicInteger(-1);
		AtomicInteger fileColIndex = new AtomicInteger(-1);
		AtomicReference<CellStyle> numberCellStyle = new AtomicReference<>();
		AtomicReference<CellStyle> dateCellStyle = new AtomicReference<>();
		List<Map.Entry<Integer, Date>> excelRecordsToBeUpdated = new ArrayList<>();
		Integer result = readOrCreateExcel(
			excelFileName,
			workBook -> {
				Sheet sheet = workBook.getSheet("Risultati");
				dataColIndex.set(Shared.getCellIndex(sheet, DATA_LABEL));
				dataAggStoricoColIndex.set(Shared.getCellIndex(sheet, DATA_AGGIORNAMENTO_STORICO_LABEL));
				fileColIndex.set(Shared.getCellIndex(sheet, FILE_LABEL));
				for (int i = 2; i < sheet.getPhysicalNumberOfRows(); i++) {
					Row row = sheet.getRow(i);
					if (rowRefersTo(row, configurationName)) {
						Date dataAggStor = row.getCell(dataAggStoricoColIndex.get()).getDateCellValue();
						if (dataAggStor == null || dataAggStor.compareTo(sEStats.getLatestExtractionDate()) < 0) {
							excelRecordsToBeUpdated.add(new AbstractMap.SimpleEntry<>(i, row.getCell(dataColIndex.get()).getDateCellValue()));
						}
					}
				}
			},
			null,
			null,
			CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false")
		);
		if (isSlave) {
			if (result.compareTo(-1) == 0) {
				return result;
			}
			Collections.shuffle(excelRecordsToBeUpdated);
		}
		int rowProcessedCounter = 0;
		for (Map.Entry<Integer, Date> rowIndexAndExtractionDate : excelRecordsToBeUpdated) {
			++rowProcessedCounter;
			AtomicReference<PersistentStorage> storageWrapper = new AtomicReference<>();
			if (simulatorFinished.get()) {
				setThreadPriorityToMax();
				int remainedRecords = (excelRecordsToBeUpdated.size() - (rowProcessedCounter + 1));
				if (remainedRecords % 100 == 0) {
					LogUtils.info("History update is going to finish: " + remainedRecords + " records remained");
				}
			}
			result = readOrCreateExcel(
				excelFileName,
				workBook -> {
					Sheet sheet = workBook.getSheet("Risultati");
					Row row = sheet.getRow(rowIndexAndExtractionDate.getKey());
					if (rowRefersTo(row, configurationName)) {
						Date dataAggStor = row.getCell(dataAggStoricoColIndex.get()).getDateCellValue();
						if (dataAggStor == null || dataAggStor.compareTo(sEStats.getLatestExtractionDate()) < 0) {
							PersistentStorage storage = PersistentStorage.restore(
								configuration.getProperty("group"),
								row.getCell(fileColIndex.get()).getStringCellValue()
							);
							storageWrapper.set(storage);
						}
					}
				},
				null,
				null,
				CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false")
			);
			if (isSlave) {
				return result;
			}
			String extractionDateFormattedForFile = TimeUtils.getDefaultDateFmtForFilePrefix().format(rowIndexAndExtractionDate.getValue());
			if (storageWrapper.get() != null) {
				Map<String, Object> premiumCountersData = premiumCountersForFile.computeIfAbsent(storageWrapper.get().getName() + extractionDateFormattedForFile, key -> {
					PersistentStorage storage = storageWrapper.get();
					File premiumCountersFile = new File(
						storage.getAbsolutePathWithoutExtension() +
						extractionDateFormattedForFile +
						"-historical-data.json"
					);
					if (!premiumCountersFile.exists()) {
						return computePremiumCountersData(sEStats, storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile);
					} else {
						Map<String, Object> data = null;
						try {
							data = readPremiumCountersData(premiumCountersFile);
						} catch (IOException exc) {
							LogUtils.error("Unable to read file " + premiumCountersFile.getAbsolutePath() + ": it will be deleted and recreated");
							if (!premiumCountersFile.delete()) {
								Throwables.sneakyThrow(exc);
							}
							return computePremiumCountersData(sEStats, storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile);
						}
						if (LocalDate.parse(
							(String)data.get("referenceDate"),
							TimeUtils.defaultLocalDateFormat
							).compareTo(TimeUtils.toLocalDate(sEStats.getLatestExtractionDate())) < 0
						) {
							return computePremiumCountersData(sEStats, storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile);
						}
						return data;
					}
				});
				if (!isSlave) {
					readOrCreateExcel(
						excelFileName,
						workBook -> {
							Sheet sheet = workBook.getSheet("Risultati");
							if (rowIndexAndExtractionDate.getKey() == 2) {
								numberCellStyle.set(workBook.createCellStyle());
								numberCellStyle.get().setAlignment(HorizontalAlignment.RIGHT);
								numberCellStyle.get().setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
								dateCellStyle.set(workBook.createCellStyle());
								dateCellStyle.get().setAlignment(HorizontalAlignment.CENTER);
								dateCellStyle.get().setDataFormat(workBook.createDataFormat().getFormat("dd/MM/yyyy"));
							} else {
								Row previousRow = sheet.getRow(rowIndexAndExtractionDate.getKey() -1);
								dateCellStyle.set(previousRow.getCell(dataAggStoricoColIndex.get()).getCellStyle());
								numberCellStyle.set(
									previousRow.getCell(Shared.getCellIndex(sheet, getHistoryPremiumLabel(Premium.allLabels().get(0)))).getCellStyle()
								);
							}
							Row row = sheet.getRow(rowIndexAndExtractionDate.getKey());
							Storage storage = storageWrapper.get();
							if (storage.getName().equals(row.getCell(fileColIndex.get()).getStringCellValue())) {
								Cell dataAggStoricoCell = row.getCell(dataAggStoricoColIndex.get());
								for (Map.Entry<Integer, String> premiumData : allPremiums.entrySet()) {
									Cell historyDataCell =
										row.getCell(Shared.getCellIndex(sheet, getHistoryPremiumLabel(premiumData.getValue())));
									Integer premiumCounter = ((Map<Integer,Integer>)premiumCountersData.get("premiumCounters.all")).get(premiumData.getKey());
									if (premiumCounter != null) {
										historyDataCell.setCellValue(premiumCounter.doubleValue());
									} else {
										historyDataCell.setCellValue(0d);
									}
									historyDataCell =
										row.getCell(Shared.getCellIndex(sheet, getFollowingProgressiveHistoryPremiumLabel(premiumData.getValue())));
									premiumCounter = ((Map<Integer,Integer>)premiumCountersData.get("premiumCounters.fromExtractionDate")).get(premiumData.getKey());
									if (premiumCounter != null) {
										historyDataCell.setCellValue(premiumCounter.doubleValue());
										if (premiumData.getKey().compareTo(Premium.TYPE_TOMBOLA) == 0 && premiumCounter > 0) {
											Shared.toHighlightedBoldedCell(workBook, historyDataCell, IndexedColors.RED);
										} else if (premiumData.getKey().compareTo(Premium.TYPE_CINQUINA) == 0 && premiumCounter > 0) {
											Shared.toHighlightedBoldedCell(workBook, historyDataCell, IndexedColors.ORANGE);
										}
									} else {
										historyDataCell.setCellValue(0d);
									}
									historyDataCell.setCellStyle(numberCellStyle.get());
								}
								Cell cell = row.getCell(Shared.getCellIndex(sheet, COSTO_STORICO_LABEL));
								cell.setCellStyle(numberCellStyle.get());
								cell.setCellValue(
									(Integer)premiumCountersData.get("premiumCounters.all.processedExtractionDateCounter") * row.getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).getNumericCellValue()
								);
								cell = row.getCell(Shared.getCellIndex(sheet, COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL));
								cell.setCellStyle(numberCellStyle.get());
								cell.setCellValue(
									(Integer)premiumCountersData.get("premiumCounters.fromExtractionDate.processedExtractionDateCounter") * row.getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).getNumericCellValue()
								);
								dataAggStoricoCell.setCellStyle(dateCellStyle.get());
								dataAggStoricoCell.setCellValue(sEStats.getLatestExtractionDate());
							}
						},
						null,
						workBook -> {
							store(excelFileName, workBook, isSlave);
							Row row = workBook.getSheet("Risultati").getRow(rowIndexAndExtractionDate.getKey());
							LogUtils.info(
								"Aggiornamento storico completato per " +
								TimeUtils.getDefaultDateFormat().format(row.getCell(0).getDateCellValue()) + " - " +
								row.getCell(fileColIndex.get()).getStringCellValue()
							);
						},
						isSlave
					);
				}
			}
		}
		//Puliamo file json duplicati da google drive
		for (File file : ResourceUtils.INSTANCE.find("(1)", "json", retrieveBasePath(configuration))) {
			file.delete();
		}
		return 1;
	}

	protected static String retrieveBasePath(Properties configuration) {
		return Optional.ofNullable(configuration.getProperty("simulation.group")).map(groupName -> {
			return PersistentStorage.buildWorkingPath(groupName);
		}).orElseGet(() -> PersistentStorage.buildWorkingPath());
	}

	protected static SEStats getSEStats(Properties configuration) {
		return SEStats.get(
			configuration.getProperty(
				"competition.archive.start-date",
				new SELotteryMatrixGeneratorEngine().getDefaultExtractionArchiveStartDate()
			), TimeUtils.defaultLocalDateFormat.format(LocalDate.now())
		);
	}

	protected static Map<String, Object> readPremiumCountersData(File premiumCountersFile) throws StreamReadException, DatabindException, IOException {
		Map<String, Object> data = objectMapper.readValue(premiumCountersFile, Map.class);
		data.put("premiumCounters.all",((Map<String, Integer>)objectMapper.readValue(premiumCountersFile, Map.class).get("premiumCounters")).entrySet().stream()
			.collect(Collectors.toMap(entry -> Integer.parseInt(entry.getKey()), Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new)));
		data.put("premiumCounters.all.processedExtractionDateCounter", Integer.valueOf((String)data.get("premiumCounters.all.processedExtractionDateCounter")));
		data.put("premiumCounters.fromExtractionDate",((Map<String, Integer>)objectMapper.readValue(premiumCountersFile, Map.class).get("premiumCountersFromExtractionDate")).entrySet().stream()
				.collect(Collectors.toMap(entry -> Integer.parseInt(entry.getKey()), Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new)));
		data.put("premiumCounters.fromExtractionDate.processedExtractionDateCounter", Integer.valueOf((String)data.get("premiumCounters.fromExtractionDate.processedExtractionDateCounter")));
		return data;
	}

	protected static Map<String, Object> computePremiumCountersData(
		SEStats sEStats,
		PersistentStorage storage,
		Date extractionDate,
		File premiumCountersFile
	) {
		LogUtils.info("Computing historycal data of " + storage.getName());
		Map<String, Object> qualityCheckResult = sEStats.checkQuality(storage::iterator);
		Map<String, Object> qualityCheckResultFromExtractionDate = sEStats.checkQualityFrom(storage::iterator, extractionDate);
		LogUtils.info("Computed historycal data of " + storage.getName());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("premiumCounters.all", qualityCheckResult.get("premium.counters"));
		data.put("premiumCounters.all.processedExtractionDateCounter", qualityCheckResult.get("processedExtractionDateCounter"));
		data.put("premiumCounters.fromExtractionDate", qualityCheckResultFromExtractionDate.get("premium.counters"));
		data.put("premiumCounters.fromExtractionDate.processedExtractionDateCounter", qualityCheckResultFromExtractionDate.get("processedExtractionDateCounter"));
		data.put("referenceDate", qualityCheckResult.get("referenceDate"));
		writePremiumCountersData(premiumCountersFile, data);
		return data;
	}

	private static void writePremiumCountersData(File premiumCountersFile, Map<String, Object> qualityCheckResult) {
		try {
			objectMapper.writeValue(premiumCountersFile, qualityCheckResult);
		} catch (IOException exc) {
			Throwables.sneakyThrow(exc);
		}
	}

	private static Function<LocalDate, Consumer<List<Storage>>> buildSystemProcessor(Properties configuration, String excelFileName) {
		AtomicBoolean rowAddedFlag = new AtomicBoolean(false);
		AtomicBoolean fileCreatedFlag = new AtomicBoolean(false);
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		return extractionDate -> storages -> {
			readOrCreateExcel(
				excelFileName,
				workBook -> {
					SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
					workBookTemplate.getOrCreateSheet("Risultati", true);
					while (true) {
						Row row = workBookTemplate.getCurrentRow();
						if (row == null || row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK) {
							rowAddedFlag.set(addRowData(workBookTemplate, extractionDate, storages));
							break;
						} else {
							workBookTemplate.addRow();
						}
					}
				},
				workBook -> {
					createWorkbook(workBook, excelFileName);
					fileCreatedFlag.set(true);
				},
				workBook -> {
					SEStats.clear();
					if (fileCreatedFlag.get() || rowAddedFlag.get()) {
						store(excelFileName, workBook, isSlave);
					}
				},
				isSlave
			);
		};
	}

	protected static boolean addRowData(
		SimpleWorkbookTemplate workBookTemplate,
		LocalDate extractionDate,
		List<Storage> storages
	) throws UnsupportedEncodingException {
		Storage storage = !storages.isEmpty() ? storages.get(storages.size() -1) : null;
		if (storage != null) {
			Map<String, Integer> results = allTimeStats.checkFor(extractionDate, storage::iterator);
			workBookTemplate.addCell(TimeUtils.toDate(extractionDate)).getCellStyle().setAlignment(HorizontalAlignment.CENTER);
			List<String> allPremiumLabels = Premium.allLabels();
			for (int i = 0; i < allPremiumLabels.size();i++) {
				Integer result = results.get(allPremiumLabels.get(i));
				if (result == null) {
					result = 0;
				}
				workBookTemplate.addCell(result, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.CENTER);
			}
			Cell cell = workBookTemplate.addCell(storage.size(), "#,##0");
			cell.getCellStyle().setAlignment(HorizontalAlignment.CENTER);
			int currentRowNum = cell.getRow().getRowNum()+1;
			Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati");
			List<String> allFormulas = Premium.allLabels().stream().map(
				label -> generatePremiumFormula(sheet, currentRowNum, label, lb -> lb)).collect(Collectors.toList());
			String formula = String.join("+", allFormulas);
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			formula = generateSaldoFormula(currentRowNum, sheet, Arrays.asList(RITORNO_LABEL, COSTO_LABEL));
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);

			workBookTemplate.addCell(Collections.nCopies(Premium.all().size(), null));
			workBookTemplate.addCell(0, "#,##0");
			allFormulas = Premium.allLabels().stream().map(
					label -> generatePremiumFormula(sheet, currentRowNum, label, SELotterySimpleSimulator::getFollowingProgressiveHistoryPremiumLabel)).collect(Collectors.toList());
			formula = String.join("+", allFormulas);
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			formula = generateSaldoFormula(currentRowNum, sheet, Arrays.asList(RITORNO_STORICO_PROGRESSIVO_ANTERIORE_LABEL, COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL));
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);

			workBookTemplate.addCell(Collections.nCopies(Premium.all().size(), null));
			workBookTemplate.addCell(0, "#,##0");
			allFormulas = Premium.allLabels().stream().map(
					label -> generatePremiumFormula(sheet, currentRowNum, label, SELotterySimpleSimulator::getHistoryPremiumLabel)).collect(Collectors.toList());
			formula = String.join("+", allFormulas);
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			formula = generateSaldoFormula(currentRowNum, sheet, Arrays.asList(RITORNO_STORICO_LABEL, COSTO_STORICO_LABEL));
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);

			workBookTemplate.addCell((Date)null);
			Cell cellName = workBookTemplate.addCell(storage.getName()).get(0);
			PersistentStorage persistentStorage = (PersistentStorage)storage;
			workBookTemplate.setLinkForCell(
				HyperlinkType.FILE,
				cellName,
				URLEncoder.encode(persistentStorage.getName(), "UTF-8").replace("+", "%20")
			);
			return true;
		}
		return false;
	}

	protected static String generateSaldoFormula(int currentRowNum, Sheet sheet, List<String> labels) {
		return String.join("-", labels.stream().map(label ->
			"(" + CellReference.convertNumToColString(Shared.getCellIndex(sheet, label))  + currentRowNum + ")"
		).collect(Collectors.toList()));
	}

	protected static String generatePremiumFormula(Sheet sheet, int currentRowNum, String columnLabel, UnaryOperator<String> transformer) {
		return "(" + CellReference.convertNumToColString(Shared.getCellIndex(sheet, transformer.apply(columnLabel))) + currentRowNum + "*" + SEStats.premiumPrice(columnLabel) + ")";
	}

	protected static String getHistoryPremiumLabel(String label) {
		return "Totale " + label.toLowerCase() + " " + STORICO_SUFFIX;
	}

	protected static String getFollowingProgressiveHistoryPremiumLabel(String label) {
		return "Totale " + label.toLowerCase() + " " + STORICO_PROGRESSIVO_ANTERIORE_SUFFIX;
	}

	protected static Function<LocalDate, Function<List<Storage>, Integer>> buildExtractionDatePredicate(
		Properties configuration,
		String excelFileName,
		Integer redundant,
		AtomicInteger redundantCounter
	) {
		String configurationName = configuration.getProperty("nameSuffix");
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		return extractionDate -> storages -> {
			AtomicReference<Integer> checkResult = new AtomicReference<Integer>();
			readOrCreateExcel(
				excelFileName,
				workBook ->
					checkResult.set(checkAlreadyProcessed(workBook, configurationName, extractionDate)),
				workBook ->
					createWorkbook(workBook, excelFileName)
				,
				workBook ->
					store(excelFileName, workBook, isSlave),
					CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false")
			);
			if (redundant != null) {
				Integer redundantCounterValue = redundantCounter.getAndIncrement();
				if (redundantCounterValue.intValue() > 0) {
					if (redundantCounterValue.intValue() < redundant) {
						checkResult.set(
							checkResult.get() != null ?
								checkResult.get() :
								0
							);
					} else {
						redundantCounter.set(1);
					}
				}
			}
			if (checkResult.get() == null) {
				checkResult.set(1);
			}
			return checkResult.get();
		};
	}

	protected static Integer checkAlreadyProcessed(
		Workbook workBook,
		String configurationName,
		LocalDate extractionDate
	) {
		Iterator<Row> rowIterator = workBook.getSheet("Risultati").rowIterator();
		rowIterator.next();
		rowIterator.next();
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (rowRefersTo(row, configurationName)) {
				Cell data = row.getCell(0);
				if (data != null && extractionDate.compareTo(TimeUtils.toLocalDate(data.getDateCellValue())) == 0) {
					return -1;
				}
			}
		}
		return null;
	}

	protected static void createWorkbook(Workbook workBook, String excelFileName) {
		SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
		Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati", true);

		List<String> summaryFormulas = new ArrayList<>();
		String columnName = CellReference.convertNumToColString(0);
		summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + getLatestRowIndex() +")");
		for (int i = 1; i < excelHeaderLabels.size()-2; i++) {
			columnName = CellReference.convertNumToColString(i);
			summaryFormulas.add(
				"FORMULA_SUM(" + columnName + "3:"+ columnName + getLatestRowIndex() +")"
			);
		}
		summaryFormulas.add("");
		summaryFormulas.add("");
		workBookTemplate.createHeader(
			"Risultati",
			true,
			Arrays.asList(
				excelHeaderLabels,
				summaryFormulas
			)
		);
		CellStyle headerNumberStyle = workBook.createCellStyle();
		headerNumberStyle.cloneStyleFrom(sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).getCellStyle());
		headerNumberStyle.setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
		for (String label : Premium.allLabels()) {
			sheet.getRow(1).getCell(Shared.getCellIndex(sheet, label)).setCellStyle(headerNumberStyle);
			sheet.getRow(1).getCell(Shared.getCellIndex(sheet, getFollowingProgressiveHistoryPremiumLabel(label))).setCellStyle(headerNumberStyle);
			sheet.getRow(1).getCell(Shared.getCellIndex(sheet, getHistoryPremiumLabel(label))).setCellStyle(headerNumberStyle);
		}
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, RITORNO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_LABEL)).setCellFormula(
			"TEXT((SUM(" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_LABEL)) + "3:" +
			CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_LABEL)) + getLatestRowIndex() +
			")/" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(COSTO_LABEL)) + "2),\"###,00%\")"
		);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, RITORNO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)).setCellFormula(
			"TEXT((SUM(" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)) + "3:" +
			CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)) + getLatestRowIndex() +
			")/" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL)) + "2),\"###,00%\")"
		);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_STORICO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, RITORNO_STORICO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_STORICO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_STORICO_LABEL)).setCellFormula(
			"TEXT((SUM(" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_STORICO_LABEL)) + "3:" +
			CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_STORICO_LABEL)) + getLatestRowIndex() +
			")/" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(COSTO_STORICO_LABEL)) + "2),\"###,00%\")"
		);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, DATA_LABEL), 3800);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, COSTO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, RITORNO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, SALDO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, COSTO_STORICO_PROGRESSIVO_ANTERIORE_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, RITORNO_STORICO_PROGRESSIVO_ANTERIORE_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, SALDO_STORICO_PROGRESSIVO_ANTERIORE_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, COSTO_STORICO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, RITORNO_STORICO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, SALDO_STORICO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, DATA_AGGIORNAMENTO_STORICO_LABEL), 3800);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, FILE_LABEL), 12000);
		//LogUtils.logInfo(PersistentStorage.buildWorkingPath() + File.separator + excelFileName + " succesfully created");
	}

	protected static Integer readOrCreateExcel(
		String excelFileName,
		ThrowingConsumer<Workbook, Throwable> action,
		ThrowingConsumer<Workbook, Throwable> createAction,
		ThrowingConsumer<Workbook, Throwable> finallyAction,
		boolean isSlave
	) {
		return readOrCreateExcelOrComputeBackups(excelFileName, null, action, createAction, finallyAction, 5, isSlave);
	}

	protected static Integer readOrCreateExcelOrComputeBackups(
		String excelFileName,
		Collection<File> backups,
		ThrowingConsumer<Workbook, Throwable> action,
		ThrowingConsumer<Workbook, Throwable> createAction,
		ThrowingConsumer<Workbook, Throwable> finallyAction,
		int slaveAdditionalReadingMaxAttempts,
		boolean isSlave
	) {
		excelFileName = excelFileName.replace("/", File.separator).replace("\\", File.separator);
		String excelFileAbsolutePath = (PersistentStorage.buildWorkingPath() + File.separator + excelFileName).replace("/", File.separator).replace("\\", File.separator);
		try {
			Synchronizer.INSTANCE.executeThrower(excelFileName, () -> {
				Workbook workBook = null;
				try {
					try (InputStream inputStream = new FileInputStream(excelFileAbsolutePath)) {
						workBook = new XSSFWorkbook(inputStream);
						action.accept(workBook);
					} catch (FileNotFoundException exc) {
						if (createAction == null) {
							throw exc;
						}
						workBook = new XSSFWorkbook();
						createAction.accept(workBook);
					}
				} finally {
					if (workBook != null) {
						if (finallyAction!= null) {
							try {
								finallyAction.accept(workBook);
							} catch (Throwable exc) {
								Throwables.sneakyThrow(exc);
							}
						}
						try {
							workBook.close();
						} catch (IOException exc) {
							Throwables.sneakyThrow(exc);
						}
					}
				}
			});
		} catch (Throwable exc) {
			if (!(exc instanceof POIXMLException || exc instanceof EmptyFileException || exc instanceof ZipException || exc instanceof PartAlreadyExistsException ||
				exc instanceof XmlValueDisconnectedException || (exc instanceof IOException && exc.getMessage().equalsIgnoreCase("Truncated ZIP file")))) {
				LogUtils.error("Unable to process file " + excelFileName);
				Throwables.sneakyThrow(exc);
			}
			if (isSlave) {
				Mutex mutex = Synchronizer.INSTANCE.getMutex(excelFileName);
				synchronized(mutex) {
					LogUtils.error("Error in Excel file '" + excelFileAbsolutePath + "'. Wating for restore by master");
					try {
						if (--slaveAdditionalReadingMaxAttempts > 0) {
							mutex.wait(5000);
						} else {
							LogUtils.error("Error in Excel file '" + excelFileAbsolutePath + "'. The file will be skipped");
							return -1;
						}
					} catch (InterruptedException e) {
						Throwables.sneakyThrow(e);
					}
				}
			} else {
				String excelFileParentPath = excelFileAbsolutePath.substring(0, excelFileAbsolutePath.lastIndexOf(File.separator));
				String effectiveExcelFileName =  excelFileAbsolutePath.substring(excelFileAbsolutePath.lastIndexOf(File.separator)+1);
				String effectiveExcelFileNameWithoutExtension = effectiveExcelFileName.substring(0, effectiveExcelFileName.lastIndexOf("."));
				String excelFileExtension = effectiveExcelFileName.substring(effectiveExcelFileName.lastIndexOf(".") +1);
				if (backups == null) {
					backups = ResourceUtils.INSTANCE.findReverseOrdered(effectiveExcelFileNameWithoutExtension + " - ", excelFileExtension, excelFileParentPath);
				}
				if (backups.isEmpty()) {
					LogUtils.error("Error in Excel file '" + excelFileAbsolutePath + "'. No backup found");
					Throwables.sneakyThrow(exc);
				}
				Iterator<File> backupsIterator = backups.iterator();
				File backup = backupsIterator.next();
				LogUtils.warn("Error in Excel file '" + excelFileAbsolutePath + "'.\nTrying to restore previous backup: '" + backup.getAbsolutePath() + "'");
				File processedFile = new File(excelFileAbsolutePath);
				if (!processedFile.delete() || !backup.renameTo(processedFile)) {
					Throwables.sneakyThrow(exc);
				}
				backupsIterator.remove();
			}
			return readOrCreateExcelOrComputeBackups(excelFileName, backups, action, createAction, finallyAction, slaveAdditionalReadingMaxAttempts, isSlave);
		}
		return 1;
	}

	protected static void store(String excelFileName, Workbook workBook, boolean isSlave) {
		Integer savingCounterForFile = savingOperationCounters.computeIfAbsent(excelFileName, key -> 0) + 1;
		File file = new File(PersistentStorage.buildWorkingPath() + File.separator + excelFileName);
		savingOperationCounters.put(excelFileName, savingCounterForFile);
		if (savingCounterForFile % 100 == 0) {
			backup(file, isSlave);
		}
		try (OutputStream destFileOutputStream = new FileOutputStream(file)){
			BaseFormulaEvaluator.evaluateAllFormulaCells(workBook);
			workBook.write(destFileOutputStream);
		} catch (IOException e) {
			Throwables.sneakyThrow(e);
		}
	}

	protected static void backup(File file, boolean isSlave) {
		ResourceUtils.INSTANCE.backup(
			file,
			file.getParentFile().getAbsolutePath()
		);
		List<File> backupFiles = ResourceUtils.INSTANCE.findOrdered("report - ", "xlsx", file.getParentFile().getAbsolutePath());
		if (backupFiles.size() > 4 && !isSlave) {
			Iterator<File> backupFileIterator = backupFiles.iterator();
			while (backupFiles.size() > 4) {
				File backupFile = backupFileIterator.next();
				backupFile.delete();
				backupFileIterator.remove();
			}
		}
	}
}
