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
import java.text.ParseException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import java.util.stream.IntStream;
import java.util.zip.ZipException;

import org.apache.poi.EmptyFileException;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.PartAlreadyExistsException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.RecordFormatException;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.impl.values.XmlValueDisconnectedException;
import org.rg.game.core.CollectionUtils;
import org.rg.game.core.ConcurrentUtils;
import org.rg.game.core.IOUtils;
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

	static final String DATA_FOLDER_NAME = "data";

	static final Map<String, Integer> savingOperationCounters = new ConcurrentHashMap<>();
	static final String BALANCE_LABEL = "Saldo";
	static final String RETURN_LABEL = "Ritorno";
	static final String COST_LABEL = "Costo";
	static final String EXTRACTION_DATE_LABEL = "Data";
	static final String HISTORICAL_LABEL = "(storico)";
	static final String HISTORICAL_BALANCE_LABEL = String.join(" ", Arrays.asList(BALANCE_LABEL, HISTORICAL_LABEL));
	static final String HISTORICAL_RETURN_LABEL = String.join(" ", Arrays.asList(RETURN_LABEL, HISTORICAL_LABEL));
	static final String HISTORICAL_COST_LABEL = String.join(" ", Arrays.asList(COST_LABEL, HISTORICAL_LABEL));
	static final String FOLLOWING_PROGRESSIVE_HISTORICAL_LABEL = "(st. prg. ant.)";
	static final String FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL = String.join(" ", Arrays.asList(BALANCE_LABEL, FOLLOWING_PROGRESSIVE_HISTORICAL_LABEL));
	static final String FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL = String.join(" ", Arrays.asList(RETURN_LABEL, FOLLOWING_PROGRESSIVE_HISTORICAL_LABEL));
	static final String FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL = String.join(" ", Arrays.asList(COST_LABEL, FOLLOWING_PROGRESSIVE_HISTORICAL_LABEL));
	static final String FILE_LABEL = "File";
	static final String HISTORICAL_UPDATE_DATE_LABEL = "Data agg. storico";
	static final List<String> reportHeaderLabels;
	static final Map<String, Integer> cellIndexesCache;

	static final ObjectMapper objectMapper = new ObjectMapper();
	static Pattern regexForExtractConfigFileName = Pattern.compile("\\[.*?\\]\\[.*?\\]\\[.*?\\](.*)\\.txt");
	static String hostName;
	static SEStats allTimeStats;

	static {
		hostName = NetworkUtils.INSTANCE.thisHostName();
		reportHeaderLabels = new ArrayList<>();
		reportHeaderLabels.add(EXTRACTION_DATE_LABEL);
		Collection<String> allPremiumLabels = Premium.allLabelsList();
		reportHeaderLabels.addAll(allPremiumLabels);
		reportHeaderLabels.add(COST_LABEL);
		reportHeaderLabels.add(RETURN_LABEL);
		reportHeaderLabels.add(BALANCE_LABEL);
		List<String> historyLabels = allPremiumLabels.stream().map(label -> getFollowingProgressiveHistoricalPremiumLabel(label)).collect(Collectors.toList());
		reportHeaderLabels.addAll(historyLabels);
		reportHeaderLabels.add(FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL);
		reportHeaderLabels.add(FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL);
		reportHeaderLabels.add(FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL);
		historyLabels = allPremiumLabels.stream().map(label -> getHistoryPremiumLabel(label)).collect(Collectors.toList());
		reportHeaderLabels.addAll(historyLabels);
		reportHeaderLabels.add(HISTORICAL_COST_LABEL);
		reportHeaderLabels.add(HISTORICAL_RETURN_LABEL);
		reportHeaderLabels.add(HISTORICAL_BALANCE_LABEL);
		reportHeaderLabels.add(HISTORICAL_UPDATE_DATE_LABEL);
		reportHeaderLabels.add(FILE_LABEL);
		cellIndexesCache = new ConcurrentHashMap<String, Integer>();
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
		String[] configurationFileFolders = ResourceUtils.INSTANCE.pathsFromSystemEnv(
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

	private static Integer getCellIndex(Sheet sheet, String label) {
		return cellIndexesCache.computeIfAbsent(label, lb -> Shared.getCellIndex(sheet, label));
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
		int maxParallelTasks = Optional.ofNullable(System.getenv("simulation.tasks.max-parallel")).map(Integer::valueOf)
			.orElseGet(() -> Math.max((Runtime.getRuntime().availableProcessors() / 2) - 1, 1));
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
			AtomicBoolean firstSetupExecuted = new AtomicBoolean(false);
			Runnable taskOperation = () -> {
				LogUtils.info("Computation of " + configuration.getProperty("file.name") + " started");
				process(configuration, excelFileName, engine, competitionDates, firstSetupExecuted);
				LogUtils.info("Computation of " + configuration.getProperty("file.name") + " succesfully finished");
			};
			if (CollectionUtils.retrieveBoolean(configuration, "async", "false")) {
				ConcurrentUtils.addTask(futures, taskOperation);
			} else {
				taskOperation.run();
			}
			ConcurrentUtils.waitUntil(futures, ft -> ft.size() >= maxParallelTasks);
			if (!firstSetupExecuted.get()) {
				synchronized (firstSetupExecuted) {
					if (!firstSetupExecuted.get()) {
						try {
							firstSetupExecuted.wait();
						} catch (InterruptedException exc) {
							Throwables.sneakyThrow(exc);
						}
					}
				}
			}
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
				simulationGroup + "/" + DATA_FOLDER_NAME
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
			workBook -> {
				if (!isSlave) {
					store(excelFileName, workBook);
				}
			},
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
			workBook -> {
				if (!isSlave) {
					store(excelFileName, workBook);
				}
			},
			isSlave
		);
	}

	private static boolean rowRefersTo(Row row, String configurationName) {
		Matcher matcher = regexForExtractConfigFileName.matcher(
			row.getCell(getCellIndex(row.getSheet(), FILE_LABEL)).getStringCellValue()
		);
		return matcher.find() && matcher.group(1).equals(configurationName);
	}

	protected static void process(
		Properties configuration,
		String excelFileName,
		SELotteryMatrixGeneratorEngine engine,
		List<List<LocalDate>> competitionDates,
		AtomicBoolean firstSetupExecuted
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
			checkAndNotifyExecutionOfFirstSetupForConfiguration(firstSetupExecuted);
			engine.getExecutor().apply(
				extractionDatePredicate
			).apply(
				systemProcessor
			);
		}
		checkAndNotifyExecutionOfFirstSetupForConfiguration(firstSetupExecuted);		updateHistorical(configuration, excelFileName, configuration.getProperty("nameSuffix"), new LinkedHashMap<>());
		if (!isSlave) {
			readOrCreateExcel(
				excelFileName,
				workBook -> {
					SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
					Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati", true);
					workBookTemplate.setAutoFilter(1, getMaxRowIndex(), 0, reportHeaderLabels.size() - 1);
					workBookTemplate.addSheetConditionalFormatting(
						new int[] {
							getCellIndex(sheet, Premium.LABEL_FIVE),
							getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_FIVE))
						},
						IndexedColors.YELLOW,
						ComparisonOperator.GT,
						new int[] {2,3},
						sh -> sh.getLastRowNum() + 1,
						"0"
					);
					workBookTemplate.addSheetConditionalFormatting(
						new int[] {
							getCellIndex(sheet, Premium.LABEL_FIVE_PLUS),
							getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_FIVE_PLUS))
						},
						IndexedColors.ORANGE,
						ComparisonOperator.GT,
						new int[] {2,3},
						sh -> sh.getLastRowNum() + 1,
						"0"
					);
					workBookTemplate.addSheetConditionalFormatting(
						new int[] {
							getCellIndex(sheet, Premium.LABEL_SIX),
							getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_SIX))
						},
						IndexedColors.RED,
						ComparisonOperator.GT,
						new int[] {2,3},
						sh -> sh.getLastRowNum() + 1,
						"0"
					);
				},
				null,
				workBook -> {
					if (!isSlave) {
						store(excelFileName, workBook);
					}
				},
				isSlave
			);
			if (!isSlave) {
				backup(new File(PersistentStorage.buildWorkingPath() + File.separator + excelFileName));
			}
		}
		//Puliamo file txt duplicati da google drive
		for (File file : ResourceUtils.INSTANCE.find("(1)", "txt", retrieveBasePath(configuration))) {
			file.delete();
		}
	}

	protected static void checkAndNotifyExecutionOfFirstSetupForConfiguration(AtomicBoolean firstSetupExecuted) {
		if (!firstSetupExecuted.get()) {
			firstSetupExecuted.set(true);
			synchronized(firstSetupExecuted) {
				firstSetupExecuted.notify();
			}
		}
	}

	private static int getMaxRowIndex() {
		return SpreadsheetVersion.EXCEL2007.getMaxRows() -1;
		//return Shared.getSEStats().getAllWinningCombos().size() * 2;
	}

	private static Integer updateHistorical(
		Properties configuration,
		String excelFileName,
		String configurationName,
		Map<String, Map<String, Object>> premiumCountersForFile
	) {
		List<Number> premiumTypeList = parseReportWinningInfoConfig(configuration.getProperty("report.winning-info", "all").replaceAll("\\s+",""));
		Number[] premiumTypes = premiumTypeList.toArray(new Number[premiumTypeList.size()]);
		boolean isSlave = CollectionUtils.retrieveBoolean(configuration, "simulation.slave", "false");
		SEStats sEStats = getSEStats(configuration);
		Function<Date, Date> dateOffsetComputer = dateOffsetComputer(configuration);
		Integer result = readOrCreateExcel(
			excelFileName,
			workBook -> {
				Sheet sheet = workBook.getSheet("Risultati");
				if (sheet.getPhysicalNumberOfRows() < 3) {
					return;
				}
				Integer extractionDateColIndex = getCellIndex(sheet, EXTRACTION_DATE_LABEL);
				Integer dataAggStoricoColIndex = getCellIndex(sheet, HISTORICAL_UPDATE_DATE_LABEL);
				Integer costColIndex = getCellIndex(sheet, COST_LABEL);
				Integer historicalCostColIndex = getCellIndex(sheet, HISTORICAL_COST_LABEL);
				Integer historicalReturnColIndex = getCellIndex(sheet, HISTORICAL_RETURN_LABEL);
				Integer followingProgressiveHistoricalCostColIndex = getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL);
				Integer followingProgressiveHistoricalReturnColIndex = getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL);
				Integer fileColIndex = getCellIndex(sheet, FILE_LABEL);
				Row firstRow = getFirstRow(sheet);
				CellStyle dateCellStyle = firstRow.getCell(extractionDateColIndex).getCellStyle();
				CellStyle numberCellStyle = firstRow.getCell(costColIndex).getCellStyle();
				CellStyle hyperLinkNumberCellStyle = null;
				if (firstRow.getCell(getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_TWO))).getCellType() == CellType.BLANK) {
					hyperLinkNumberCellStyle = workBook.createCellStyle();
					hyperLinkNumberCellStyle.cloneStyleFrom(firstRow.getCell(fileColIndex).getCellStyle());
					hyperLinkNumberCellStyle.setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
					hyperLinkNumberCellStyle.setAlignment(HorizontalAlignment.RIGHT);
				} else {
					hyperLinkNumberCellStyle = firstRow.getCell(historicalReturnColIndex).getCellStyle();
				}
				List<Integer> rowsToBeProcessed = IntStream.range(2, sheet.getPhysicalNumberOfRows()).boxed().collect(Collectors.toList());
				if (isSlave) {
					Collections.shuffle(rowsToBeProcessed);
				}
				int rowProcessedCounter = 0;
				int modifiedRowCounter = 0;
				for (int index = 0; index < rowsToBeProcessed.size(); index++) {
					++rowProcessedCounter;
					int remainedRecords = (rowsToBeProcessed.size() - (rowProcessedCounter));
					Integer rowIndex = rowsToBeProcessed.get(index);
					Row currentRow = sheet.getRow(rowIndex);
					currentRow.getCell(historicalReturnColIndex).setCellStyle(hyperLinkNumberCellStyle);
					if (rowRefersTo(currentRow, configurationName)) {
						Date dataAggStor = dateOffsetComputer.apply(currentRow.getCell(dataAggStoricoColIndex).getDateCellValue());
						if (dataAggStor == null || dataAggStor.compareTo(sEStats.getLatestExtractionDate()) < 0) {
							Map.Entry<Integer, Date> rowIndexAndExtractionDate = new AbstractMap.SimpleEntry<>(rowIndex, currentRow.getCell(extractionDateColIndex).getDateCellValue());
							AtomicReference<PersistentStorage> storageWrapper = new AtomicReference<>();
							storageWrapper.set(
								PersistentStorage.restore(
									configuration.getProperty("group"),
									currentRow.getCell(fileColIndex).getStringCellValue()
								)
							);
							String extractionDateFormattedForFile = TimeUtils.getDefaultDateFmtForFilePrefix().format(rowIndexAndExtractionDate.getValue());
							if (storageWrapper.get() != null) {
								Map<String, Object> premiumCountersData = premiumCountersForFile.computeIfAbsent(storageWrapper.get().getName() + extractionDateFormattedForFile, key -> {
									PersistentStorage storage = storageWrapper.get();
									File premiumCountersFile = new File(
										new File(storage.getAbsolutePath()).getParentFile().getAbsolutePath() + File.separator + storage.getNameWithoutExtension() +
										extractionDateFormattedForFile +
										"-historical-data.json"
									);
									if (!premiumCountersFile.exists()) {
										return computePremiumCountersData(sEStats, storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile, premiumTypes);
									} else {
										Map<String, Object> data = null;
										try {
											data = readPremiumCountersData(storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile);
										} catch (IOException exc) {
											LogUtils.error("Unable to read file " + premiumCountersFile.getAbsolutePath() + ": it will be deleted and recreated");
											if (!premiumCountersFile.delete()) {
												Throwables.sneakyThrow(exc);
											}
											return computePremiumCountersData(sEStats, storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile, premiumTypes);
										}
										try {
											if (dateOffsetComputer.apply(TimeUtils.getDefaultDateFormat().parse((String)data.get("referenceDate")))
												.compareTo(sEStats.getLatestExtractionDate()) < 0
											) {
												return computePremiumCountersData(sEStats, storage, rowIndexAndExtractionDate.getValue(), premiumCountersFile, premiumTypes);
											}
										} catch (ParseException exc) {
											return Throwables.sneakyThrow(exc);
										}
										return data;
									}
								});
								if (!isSlave) {
									++modifiedRowCounter;
									Storage storage = storageWrapper.get();
									if (storage.getName().equals(currentRow.getCell(fileColIndex).getStringCellValue())) {
										Cell dataAggStoricoCell = currentRow.getCell(dataAggStoricoColIndex);
										for (Map.Entry<Number, String> premiumData :  Premium.all().entrySet()) {
											Cell historyDataCell =
												currentRow.getCell(getCellIndex(sheet, getHistoryPremiumLabel(premiumData.getValue())));
											Number premiumCounter = ((Map<Number,Integer>)premiumCountersData.get("premiumCounters.all")).get(premiumData.getKey());
											if (premiumCounter != null) {
												historyDataCell.setCellValue(premiumCounter.doubleValue());
											} else {
												historyDataCell.setCellValue(0d);
											}
											historyDataCell =
												currentRow.getCell(getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(premiumData.getValue())));
											premiumCounter = ((Map<Number,Integer>)premiumCountersData.get("premiumCounters.fromExtractionDate")).get(premiumData.getKey());
											if (premiumCounter != null) {
												historyDataCell.setCellValue(premiumCounter.doubleValue());
											} else {
												historyDataCell.setCellValue(0d);
											}
											historyDataCell.setCellStyle(numberCellStyle);
										}
										Cell cell = currentRow.getCell(historicalCostColIndex);
										cell.setCellStyle(numberCellStyle);
										cell.setCellValue(
											(Integer)premiumCountersData.get("premiumCounters.all.processedExtractionDateCounter") * currentRow.getCell(costColIndex).getNumericCellValue()
										);
										cell = currentRow.getCell(followingProgressiveHistoricalCostColIndex);
										cell.setCellStyle(numberCellStyle);
										cell.setCellValue(
											(Integer)premiumCountersData.get("premiumCounters.fromExtractionDate.processedExtractionDateCounter") * currentRow.getCell(costColIndex).getNumericCellValue()
										);
										File reportDetailFileFromExtractionDate = (File) premiumCountersData.get("reportDetailFile.fromExtractionDate");
										if (reportDetailFileFromExtractionDate != null) {
											SimpleWorkbookTemplate.setLinkForCell(
												workBook,
												HyperlinkType.FILE,
												currentRow.getCell(followingProgressiveHistoricalReturnColIndex),
												hyperLinkNumberCellStyle,
												reportDetailFileFromExtractionDate.getParentFile().getName() + File.separator + reportDetailFileFromExtractionDate.getName()
											);
										}
										File reportDetailFile = (File) premiumCountersData.get("reportDetailFile.all");
										if (reportDetailFile != null) {
											SimpleWorkbookTemplate.setLinkForCell(
												workBook,
												HyperlinkType.FILE,
												currentRow.getCell(historicalReturnColIndex),
												hyperLinkNumberCellStyle,
												reportDetailFile.getParentFile().getName() + File.separator + reportDetailFile.getName()
											);
										}
										dataAggStoricoCell.setCellStyle(dateCellStyle);
										dataAggStoricoCell.setCellValue(sEStats.getLatestExtractionDate());
									}
									if ((modifiedRowCounter % 10) == 0) {
										LogUtils.info("Storing historical data of " + excelFileName);
										store(excelFileName, workBook);
									}
								}
							}
						}
					}
					if (remainedRecords % 100 == 0) {
						LogUtils.info("Historical update remained records of " + excelFileName + ": " + remainedRecords);
					}
				}
			},
			null,
			workBook -> {
				if (!isSlave) {
					LogUtils.info("Final historical data storing of " + excelFileName);
					store(excelFileName, workBook);
				}
			},
			isSlave
		);
		//Puliamo file json duplicati da google drive
		for (File file : ResourceUtils.INSTANCE.find("(1)", "json", retrieveBasePath(configuration))) {
			file.delete();
		}
		return 1;
	}

	protected static Row getFirstRow(Sheet sheet) {
		return sheet.getRow(2);
	}

	private static List<Number> parseReportWinningInfoConfig(String reportWinningInfoConfig) {
		if (reportWinningInfoConfig.equalsIgnoreCase("all")) {
			return Premium.allTypesList();
		} else if (reportWinningInfoConfig.equalsIgnoreCase("high")) {
			return Premium.allHighTypesList();
		} else {
			List<Number> premiumsTypes = new ArrayList<>();
			for (String configPremiumLabel : reportWinningInfoConfig.split(",")) {
				if (configPremiumLabel.equalsIgnoreCase("high")) {
					premiumsTypes.addAll(Premium.allHighTypesList());
					continue;
				}
				for (String premiumLabel : Premium.allLabelsList()) {
					if (configPremiumLabel.equalsIgnoreCase(premiumLabel.replaceAll("\\s+",""))) {
						premiumsTypes.add(Premium.toType(premiumLabel));
						break;
					}
				}
			}
			return premiumsTypes;
		}
	}

	private static Function<Date, Date> dateOffsetComputer(Properties configuration) {
		String offsetRaw = configuration.getProperty("history.validity", "0d");
		String offsetAsString = offsetRaw.replaceAll("\\s+","").split("d|D|w|W|m|M")[0];
		Integer offset = Integer.valueOf(offsetAsString);
		if (offset.compareTo(0) == 0) {
			return date -> date;
		}
		String incrementationType = String.valueOf(offsetRaw.charAt(offsetRaw.length()-1));
		if (incrementationType.equalsIgnoreCase("w")) {
			return date ->
				date != null ?
					TimeUtils.increment(date, offset, ChronoUnit.WEEKS) :
					null;
		} else if (incrementationType.equalsIgnoreCase("m")) {
			return date ->
				date != null ?
					TimeUtils.increment(date, offset, ChronoUnit.MONTHS) :
					null;
		}
		return date ->
			date != null ?
				TimeUtils.increment(date, offset, ChronoUnit.DAYS) :
				null;
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

	protected static Map<String, Object> readPremiumCountersData(
		PersistentStorage storage,
		Date extractionDate,
		File premiumCountersFile
	) throws StreamReadException, DatabindException, IOException {
		Map<String, Object> data = objectMapper.readValue(premiumCountersFile, Map.class);
		data.put("premiumCounters.all",((Map<String, Integer>)data.get("premiumCounters.all")).entrySet().stream()
			.collect(Collectors.toMap(entry -> Premium.parseType(entry.getKey()), Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new)));
		data.put("premiumCounters.fromExtractionDate",((Map<String, Integer>)data.get("premiumCounters.fromExtractionDate")).entrySet().stream()
			.collect(Collectors.toMap(entry ->Premium.parseType(entry.getKey()), Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new)));
		String basePath = new File(storage.getAbsolutePath()).getParentFile().getAbsolutePath() + File.separator + storage.getNameWithoutExtension();

		File reportDetailFile = new File(basePath + "-historical-premiums.txt");
		if (reportDetailFile.exists()) {
			data.put("reportDetailFile.all", reportDetailFile);
		}
		File reportDetailFileFromExtractionDate = new File(
			basePath + "-historical-premiums" +
			TimeUtils.getDefaultDateFmtForFilePrefix().format(extractionDate) + ".txt"
		);
		if (reportDetailFileFromExtractionDate.exists()) {
			data.put("reportDetailFile.fromExtractionDate", reportDetailFileFromExtractionDate);
		}
		return data;
	}

	protected static Map<String, Object> computePremiumCountersData(
		SEStats sEStats,
		PersistentStorage storage,
		Date extractionDate,
		File premiumCountersFile,
		Number... premiumTypes
	) {
		//LogUtils.info("Computing historycal data of " + storage.getName());
		Map<String, Object> qualityCheckResult =
			sEStats.checkQuality(storage::iterator, Premium.allTypes(), premiumTypes);
		Map<String, Object> qualityCheckResultFromExtractionDate =
			sEStats.checkQualityFrom(storage::iterator, extractionDate,  Premium.allTypes(), premiumTypes);
		String reportDetail = (String)qualityCheckResult.get("report.detail");
		String reportDetailFromExtractionDate = (String)qualityCheckResultFromExtractionDate.get("report.detail");
		String basePath = new File(storage.getAbsolutePath()).getParentFile().getAbsolutePath() + File.separator + storage.getNameWithoutExtension();
		File reportDetailFile = IOUtils.INSTANCE.writeToNewFile(basePath + "-historical-premiums.txt", reportDetail);
		File reportDetailFileFromExtractionDate = IOUtils.INSTANCE.writeToNewFile(
			basePath + "-historical-premiums" +
			TimeUtils.getDefaultDateFmtForFilePrefix().format(extractionDate) + ".txt", reportDetailFromExtractionDate
		);
		LogUtils.info("Computed historycal data of " + storage.getName());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("premiumCounters.all", qualityCheckResult.get("premium.counters"));
		data.put("premiumCounters.all.processedExtractionDateCounter", qualityCheckResult.get("processedExtractionDateCounter"));
		data.put("premiumCounters.fromExtractionDate", qualityCheckResultFromExtractionDate.get("premium.counters"));
		data.put("premiumCounters.fromExtractionDate.processedExtractionDateCounter", qualityCheckResultFromExtractionDate.get("processedExtractionDateCounter"));
		data.put("referenceDate", qualityCheckResult.get("referenceDate"));
		writePremiumCountersData(premiumCountersFile, data);
		data.put("reportDetailFile.all", reportDetailFile);
		data.put("reportDetailFile.fromExtractionDate", reportDetailFileFromExtractionDate);
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
					Sheet sheet = workBook.getSheet("Risultati");
					Storage storage = !storages.isEmpty() ? storages.get(storages.size() -1) : null;
					if (storage != null) {
						Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
						addRowData(row, extractionDate, (PersistentStorage)storage);
						rowAddedFlag.set(true);
					}
				},
				workBook -> {
					createWorkbook(workBook, excelFileName);
					fileCreatedFlag.set(true);
				},
				workBook -> {
					SEStats.clear();
					if (!isSlave && (fileCreatedFlag.get() || rowAddedFlag.get())) {
						store(excelFileName, workBook);
					}
				},
				isSlave
			);
		};
	}

	protected static void addRowData(
		Row row,
		LocalDate extractionDate,
		PersistentStorage storage
	) throws UnsupportedEncodingException {
		Sheet sheet = row.getSheet();
		Workbook workBook = sheet.getWorkbook();
		CellStyle numberCellStyle = null;
		CellStyle dateCellStyle = null;
		CellStyle hyperLinkStyle = null;
		if (row.getRowNum() > 2) {
			Row firstRow = getFirstRow(sheet);
			numberCellStyle = firstRow.getCell(getCellIndex(row.getSheet(), BALANCE_LABEL)).getCellStyle();
			dateCellStyle = firstRow.getCell(getCellIndex(row.getSheet(), EXTRACTION_DATE_LABEL)).getCellStyle();
			hyperLinkStyle = firstRow.getCell(getCellIndex(row.getSheet(), FILE_LABEL)).getCellStyle();
		} else {
			numberCellStyle = workBook.createCellStyle();
			numberCellStyle.setAlignment(HorizontalAlignment.RIGHT);
			DataFormat dataFormat = workBook.createDataFormat();
			numberCellStyle.setDataFormat(dataFormat.getFormat("#,##0"));

			dateCellStyle = workBook.createCellStyle();
			dateCellStyle.setAlignment(HorizontalAlignment.CENTER);
			dateCellStyle.setDataFormat(dataFormat.getFormat("dd/MM/yyyy"));

			hyperLinkStyle = workBook.createCellStyle();
			Font fontStyle = workBook.createFont();
			fontStyle.setColor(IndexedColors.BLUE.index);
			fontStyle.setUnderline(XSSFFont.U_SINGLE);
			hyperLinkStyle.setFont(fontStyle);
		}
		Map<String, Integer> results = allTimeStats.checkFor(extractionDate, storage::iterator);
		Cell cell = row.createCell(getCellIndex(row.getSheet(), EXTRACTION_DATE_LABEL));
		cell.setCellStyle(dateCellStyle);
		cell.setCellValue(TimeUtils.toDate(extractionDate));

		List<String> allPremiumLabels = Premium.allLabelsList();
		for (int i = 0; i < allPremiumLabels.size();i++) {
			String label = allPremiumLabels.get(i);
			Integer result = results.get(allPremiumLabels.get(i));
			if (result == null) {
				result = 0;
			}
			cell = row.createCell(getCellIndex(row.getSheet(), label));
			cell.setCellStyle(numberCellStyle);
			cell.setCellValue(result);
		}
		cell = row.createCell(getCellIndex(row.getSheet(), COST_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellValue(storage.size());

		List<String> allFormulas = Premium.allLabelsList().stream().map(
			label -> generatePremiumFormula(sheet, row.getRowNum(), label, lb -> lb)).collect(Collectors.toList());
		String formula = String.join("+", allFormulas);
		cell = row.createCell(getCellIndex(row.getSheet(), RETURN_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellFormula(formula);

		formula = generateBalanceFormula(row.getRowNum(), sheet, Arrays.asList(RETURN_LABEL, COST_LABEL));
		cell = row.createCell(getCellIndex(row.getSheet(), BALANCE_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellFormula(formula);

		for (int i = 0; i < allPremiumLabels.size();i++) {
			cell = row.createCell(getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(allPremiumLabels.get(i))));
			cell.setCellStyle(numberCellStyle);
		}
		cell = row.createCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellValue(0);

		allFormulas = Premium.allLabelsList().stream().map(
				label -> generatePremiumFormula(sheet, row.getRowNum(), label, SELotterySimpleSimulator::getFollowingProgressiveHistoricalPremiumLabel)).collect(Collectors.toList());
		formula = String.join("+", allFormulas);
		cell = row.createCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellFormula(formula);

		formula = generateBalanceFormula(row.getRowNum(), sheet, Arrays.asList(FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL, FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL));
		cell = row.createCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellFormula(formula);

		for (int i = 0; i < allPremiumLabels.size();i++) {
			cell = row.createCell(getCellIndex(sheet, getHistoryPremiumLabel(allPremiumLabels.get(i))));
			cell.setCellStyle(numberCellStyle);
		}

		cell = row.createCell(getCellIndex(sheet, HISTORICAL_COST_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellValue(0);

		allFormulas = Premium.allLabelsList().stream().map(
				label -> generatePremiumFormula(sheet, row.getRowNum(), label, SELotterySimpleSimulator::getHistoryPremiumLabel)).collect(Collectors.toList());
		formula = String.join("+", allFormulas);
		cell = row.createCell(getCellIndex(sheet, HISTORICAL_RETURN_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellFormula(formula);

		formula = generateBalanceFormula(row.getRowNum(), sheet, Arrays.asList(HISTORICAL_RETURN_LABEL, HISTORICAL_COST_LABEL));
		cell = row.createCell(getCellIndex(sheet, HISTORICAL_BALANCE_LABEL));
		cell.setCellStyle(numberCellStyle);
		cell.setCellFormula(formula);

		cell = row.createCell(getCellIndex(row.getSheet(), HISTORICAL_UPDATE_DATE_LABEL));
		cell.setCellStyle(dateCellStyle);

		Hyperlink hyperLink = workBook.getCreationHelper().createHyperlink(HyperlinkType.FILE);
		try {
			hyperLink.setAddress(URLEncoder.encode(new File(storage.getAbsolutePath()).getParentFile().getName() + File.separator + storage.getName(), "UTF-8").replace("+", "%20"));
		} catch (UnsupportedEncodingException exc) {
			Throwables.sneakyThrow(exc);
		}
		cell = row.createCell(getCellIndex(row.getSheet(), FILE_LABEL));
		cell.setCellValue(storage.getName());
		cell.setHyperlink(hyperLink);
		cell.setCellStyle(hyperLinkStyle);
	}

	protected static String generateBalanceFormula(int currentRowNum, Sheet sheet, List<String> labels) {
		return String.join("-", labels.stream().map(label ->
			"(" + CellReference.convertNumToColString(getCellIndex(sheet, label))  + currentRowNum + ")"
		).collect(Collectors.toList()));
	}

	protected static String generatePremiumFormula(Sheet sheet, int currentRowNum, String columnLabel, UnaryOperator<String> transformer) {
		return "(" + CellReference.convertNumToColString(getCellIndex(sheet, transformer.apply(columnLabel))) + currentRowNum + "*" + SEStats.premiumPrice(columnLabel) + ")";
	}

	protected static String getHistoryPremiumLabel(String label) {
		return "Totale " + label.toLowerCase() + " " + HISTORICAL_LABEL;
	}

	protected static String getFollowingProgressiveHistoricalPremiumLabel(String label) {
		return "Totale " + label.toLowerCase() + " " + FOLLOWING_PROGRESSIVE_HISTORICAL_LABEL;
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
				workBook -> {
					if (!isSlave) {
						store(excelFileName, workBook);
					}
				},
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
		summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + getMaxRowIndex() +")");
		for (int i = 1; i < reportHeaderLabels.size()-2; i++) {
			columnName = CellReference.convertNumToColString(i);
			summaryFormulas.add(
				"FORMULA_SUM(" + columnName + "3:"+ columnName + getMaxRowIndex() +")"
			);
		}
		summaryFormulas.add("");
		summaryFormulas.add("");
		workBookTemplate.createHeader(
			"Risultati",
			true,
			Arrays.asList(
				reportHeaderLabels,
				summaryFormulas
			)
		);
		CellStyle headerNumberStyle = workBook.createCellStyle();
		headerNumberStyle.cloneStyleFrom(sheet.getRow(1).getCell(getCellIndex(sheet, COST_LABEL)).getCellStyle());
		headerNumberStyle.setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
		for (String label : Premium.allLabelsList()) {
			sheet.getRow(1).getCell(getCellIndex(sheet, label)).setCellStyle(headerNumberStyle);
			sheet.getRow(1).getCell(getCellIndex(sheet, getFollowingProgressiveHistoricalPremiumLabel(label))).setCellStyle(headerNumberStyle);
			sheet.getRow(1).getCell(getCellIndex(sheet, getHistoryPremiumLabel(label))).setCellStyle(headerNumberStyle);
		}
		sheet.getRow(1).getCell(getCellIndex(sheet, COST_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, RETURN_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, BALANCE_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, BALANCE_LABEL)).setCellFormula(
			"TEXT((SUM(" + CellReference.convertNumToColString(reportHeaderLabels.indexOf(BALANCE_LABEL)) + "3:" +
			CellReference.convertNumToColString(reportHeaderLabels.indexOf(BALANCE_LABEL)) + getMaxRowIndex() +
			")/" + CellReference.convertNumToColString(reportHeaderLabels.indexOf(COST_LABEL)) + "2),\"###,00%\")"
		);
		sheet.getRow(1).getCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL)).setCellFormula(
			"TEXT((SUM(" + CellReference.convertNumToColString(reportHeaderLabels.indexOf(FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL)) + "3:" +
			CellReference.convertNumToColString(reportHeaderLabels.indexOf(FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL)) + getMaxRowIndex() +
			")/" + CellReference.convertNumToColString(reportHeaderLabels.indexOf(FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL)) + "2),\"###,00%\")"
		);
		sheet.getRow(1).getCell(getCellIndex(sheet, HISTORICAL_COST_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, HISTORICAL_RETURN_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, HISTORICAL_BALANCE_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(getCellIndex(sheet, HISTORICAL_BALANCE_LABEL)).setCellFormula(
			"TEXT((SUM(" + CellReference.convertNumToColString(reportHeaderLabels.indexOf(HISTORICAL_BALANCE_LABEL)) + "3:" +
			CellReference.convertNumToColString(reportHeaderLabels.indexOf(HISTORICAL_BALANCE_LABEL)) + getMaxRowIndex() +
			")/" + CellReference.convertNumToColString(reportHeaderLabels.indexOf(HISTORICAL_COST_LABEL)) + "2),\"###,00%\")"
		);
		sheet.setColumnWidth(getCellIndex(sheet, EXTRACTION_DATE_LABEL), 3800);
		sheet.setColumnWidth(getCellIndex(sheet, COST_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, RETURN_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, BALANCE_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, FOLLOWING_PROGRESSIVE_HISTORICAL_BALANCE_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, HISTORICAL_COST_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, HISTORICAL_RETURN_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, HISTORICAL_BALANCE_LABEL), 3000);
		sheet.setColumnWidth(getCellIndex(sheet, HISTORICAL_UPDATE_DATE_LABEL), 3800);
		sheet.setColumnWidth(getCellIndex(sheet, FILE_LABEL), 12000);
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
				exc instanceof XmlValueDisconnectedException || exc instanceof RecordFormatException || (exc instanceof IOException && exc.getMessage().equalsIgnoreCase("Truncated ZIP file")))) {
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

	protected static void store(String excelFileName, Workbook workBook) {
		Integer savingCounterForFile = savingOperationCounters.computeIfAbsent(excelFileName, key -> 0) + 1;
		File file = new File(PersistentStorage.buildWorkingPath() + File.separator + excelFileName);
		savingOperationCounters.put(excelFileName, savingCounterForFile);
		if (savingCounterForFile % 50 == 0) {
			backup(file);
		}
		try (OutputStream destFileOutputStream = new FileOutputStream(file)){
			BaseFormulaEvaluator.evaluateAllFormulaCells(workBook);
			workBook.write(destFileOutputStream);
		} catch (IOException e) {
			Throwables.sneakyThrow(e);
		}
	}

	protected static void backup(File file) {
		ResourceUtils.INSTANCE.backup(
			file,
			file.getParentFile().getAbsolutePath()
		);
		List<File> backupFiles = ResourceUtils.INSTANCE.findOrdered("report - ", "xlsx", file.getParentFile().getAbsolutePath());
		if (backupFiles.size() > 4) {
			Iterator<File> backupFileIterator = backupFiles.iterator();
			while (backupFiles.size() > 4) {
				File backupFile = backupFileIterator.next();
				backupFile.delete();
				backupFileIterator.remove();
			}
		}
	}
}
