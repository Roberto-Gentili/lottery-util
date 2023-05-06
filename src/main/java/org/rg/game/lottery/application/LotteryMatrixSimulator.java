package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.time.LocalDate;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.CollectionUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.Synchronizer;
import org.rg.game.core.ThrowingConsumer;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;
import org.rg.game.lottery.engine.Storage;

import com.fasterxml.jackson.databind.ObjectMapper;



public class LotteryMatrixSimulator {
	private static final Map<String, Integer> savingOperationCounters = new ConcurrentHashMap<>();
	private static final String SALDO_LABEL = "Saldo";
	private static final String RITORNO_LABEL = "Ritorno";
	private static final String COSTO_LABEL = "Costo";
	private static final String DATA_LABEL = "Data";
	private static final String SALDO_STORICO_LABEL = "Saldo (storico)";
	private static final String RITORNO_STORICO_LABEL = "Ritorno (storico)";
	private static final String COSTO_STORICO_LABEL = "Costo (storico)";
	private static final String FILE_LABEL = "File";
	private static final String DATA_AGGIORNAMENTO_STORICO_LABEL = "Data agg. storico";
	private static final List<String> excelHeaderLabels;

	private static final ObjectMapper objectMapper = new ObjectMapper();
	static Pattern regexForExtractConfigFileName = Pattern.compile("\\[.*?\\]\\[.*?\\]\\[.*?\\](.*)\\.txt");
	static String hostName;
	static SEStats allTimeStats;

	static {
		excelHeaderLabels = new ArrayList<>();
		excelHeaderLabels.add(DATA_LABEL);
		Collection<String> allPremiumLabels = SEStats.allPremiumLabels();
		excelHeaderLabels.addAll(allPremiumLabels);
		excelHeaderLabels.add(COSTO_LABEL);
		excelHeaderLabels.add(RITORNO_LABEL);
		excelHeaderLabels.add(SALDO_LABEL);
		List<String> historyLabels = allPremiumLabels.stream().map(label -> getHistoryPremiumLabel(label)).collect(Collectors.toList());
		excelHeaderLabels.addAll(historyLabels);
		excelHeaderLabels.add(COSTO_STORICO_LABEL);
		excelHeaderLabels.add(RITORNO_STORICO_LABEL);
		excelHeaderLabels.add(SALDO_STORICO_LABEL);
		excelHeaderLabels.add(DATA_AGGIORNAMENTO_STORICO_LABEL);
		excelHeaderLabels.add(FILE_LABEL);
	}

	public static void main(String[] args) throws IOException {
		hostName = InetAddress.getLocalHost().getHostName();
		ZipSecureFile.setMinInflateRatio(0);
		Collection<CompletableFuture<Void>> futures = new ArrayList<>();
		execute("se", futures);
		futures.stream().forEach(CompletableFuture::join);
	}

	private static void execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) throws IOException {
		SEStats.forceLoadingFromExcel = false;
		allTimeStats = SEStats.get("03/12/1997", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.get("02/07/2009", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		Supplier<SELotteryMatrixGeneratorEngine> engineSupplier = SELotteryMatrixGeneratorEngine::new;

		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-matrix-generator", "properties",
				PersistentStorage.buildWorkingPath(),
				ResourceUtils.INSTANCE.getResource("simulations").getAbsolutePath()
			);
		List<Properties> configurations = new ArrayList<>();
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles)) {
			String simulationDates = config.getProperty("simulation.dates");
			if (Boolean.parseBoolean(config.getProperty("simulation.enabled", "false"))) {
				if (simulationDates != null) {
					config.setProperty("competition", simulationDates);
				}
				String simulationGroup = config.getProperty("simulation.group");
				if (simulationGroup != null) {
					simulationGroup = simulationGroup.replace("${localhost.name}", hostName);
					config.setProperty("simulation.group", simulationGroup);
					config.setProperty(
						"group",
						simulationGroup
					);
				}
				config.setProperty("storage", "filesystem");
				config.setProperty("overwrite-if-exists", String.valueOf(Boolean.parseBoolean(config.getProperty("simulation.slave", "false"))? -1 : 0));
				configurations.add(config);
			}
		}

		for (Properties configuration : configurations) {
			System.out.println(
				"Processing file '" + configuration.getProperty("file.name") + "' located in '" + configuration.getProperty("file.parent.absolutePath") + "' in " +
					(Boolean.parseBoolean(configuration.getProperty("simulation.slave")) ? "slave" : "master") + " mode"
			);
			String info = configuration.getProperty("info");
			if (info != null) {
				System.out.println(info);
			}
			String excelFileName =
				Optional.ofNullable(configuration.getProperty("simulation.group")).map(groupName -> {
					PersistentStorage.buildWorkingPath(groupName);
					return groupName + File.separator + "report.xlsx";
				}).orElseGet(() -> configuration.getProperty("file.name").replace("." + configuration.getProperty("file.extension"), "") + "-sim.xlsx");
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
			if (Boolean.parseBoolean(configuration.getProperty("async", "false"))) {
				futures.add(
					CompletableFuture.runAsync(
						() ->
							process(configuration, excelFileName, engine, competitionDates)
					)
				);
			} else {
				process(configuration, excelFileName, engine, competitionDates);
			}
			if (futures.size() >= 5) {
				Iterator<CompletableFuture<Void>> futuresIterator = futures.iterator();
				while(futuresIterator.hasNext()) {
					futuresIterator.next().join();
					futuresIterator.remove();
				}
			}
		}
	}

	private static void cleanup(
		Properties configuration,
		String excelFileName,
		Collection<LocalDate> competitionDates,
		String configFileName, Integer redundancy
	) {
		Iterator<LocalDate> datesIterator = competitionDates.iterator();
		SEStats sEStats = getSEStats(configuration);
		LocalDate latestExtractionArchiveStartDate = TimeUtils.toLocalDate(sEStats.getLatestExtractionDate());
		while (datesIterator.hasNext()) {
			if (datesIterator.next().compareTo(latestExtractionArchiveStartDate) > 0) {
				datesIterator.remove();
			}
		}
		int initialSize = competitionDates.size();
		if (redundancy != null) {
			cleanupRedundant(excelFileName, configFileName, redundancy, competitionDates);
		}
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
				store(excelFileName, workBook)
		);
		System.out.println(competitionDates.size() + " dates will be processed, " + (initialSize - competitionDates.size()) + " already processed");
	}

	private static void cleanupRedundant(String excelFileName, String configFileName, Integer redundancy, Collection<LocalDate> competitionDatesFlat) {
		List<LocalDate> competionDateLatestBlock =
			CollectionUtils.toSubLists(
				new ArrayList<>(competitionDatesFlat),
				redundancy
			).stream().reduce((prev, next) -> next).orElse(null);
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
				store(excelFileName, workBook)
		);
	}

	private static boolean rowRefersTo(Row row, String configurationName) {
		Matcher matcher = regexForExtractConfigFileName.matcher(
			row.getCell(Shared.getCellIndex(row.getSheet(), FILE_LABEL)).getStringCellValue()
		);
		return matcher.find() && matcher.group(1).equals(configurationName);
	}

	private static void process(
		Properties configuration,
		String excelFileName,
		SELotteryMatrixGeneratorEngine engine,
		List<List<LocalDate>> competitionDates
	) {
		String redundantConfigValue = configuration.getProperty("simulation.redundancy");
		boolean isSlave = Boolean.parseBoolean(configuration.getProperty("simulation.slave", "false"));
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
				configuration.getProperty("nameSuffix"),
				excelFileName,
				redundantConfigValue != null? Integer.valueOf(redundantConfigValue) : null,
				redundantCounter
			);
			systemProcessor = buildSystemProcessor(excelFileName);
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
				throw new RuntimeException(exc);
			}
		}
		simulatorFinished.set(true);
		historyUpdateTask.join();
		if (!isSlave) {
			readOrCreateExcel(
				excelFileName,
				workBook -> {
					try (SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);) {
						workBookTemplate.getOrCreateSheet("Risultati", true);
						workBookTemplate.setAutoFilter(1, Shared.getSEStats().getAllWinningCombos().size() * 2, 0, excelHeaderLabels.size() - 1);
					}
				},
				null,
				workBook -> {
					store(excelFileName, workBook);
				}
			);
		}
		backup(new File(PersistentStorage.buildWorkingPath() + File.separator + excelFileName));
		System.out.println("Processing of " + configuration.getProperty("file.name") + " succesfully finished");
	}

	private static CompletableFuture<Void> startHistoryUpdateTask(
		Properties configuration,
		String excelFileName,
		String configurationName,
		AtomicBoolean historyUpdateTaskStarted,
		AtomicBoolean simulatorFinished
	) {
		return CompletableFuture.runAsync(() -> {
			boolean isSlave = Boolean.parseBoolean(configuration.getProperty("simulation.slave", "false"));
			int initialPriority = Thread.currentThread().getPriority();
			if (!isSlave) {
				if (!simulatorFinished.get()) {
					Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				} else{
					setThreadPriorityToMax();
				}
			}
			Map<String, Map<Integer, Integer>> premiumCountersForFile = new LinkedHashMap<>();
			while (!simulatorFinished.get()) {
				historyUpdateTaskStarted.set(true);
				updateHistory(configuration, excelFileName, configurationName, premiumCountersForFile, simulatorFinished);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException exc) {
					throw new RuntimeException(exc);
				}
			}
			setThreadPriorityToMax();
			updateHistory(configuration, excelFileName, configurationName, premiumCountersForFile, simulatorFinished);
			Thread.currentThread().setPriority(initialPriority);
		});

	}

	private static void setThreadPriorityToMax() {
		if (Thread.currentThread().getPriority() != Thread.MAX_PRIORITY) {
			System.out.println("Setting priority of Thread " + Thread.currentThread() + " to max");
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		}
	}

	private static void updateHistory(
		Properties configuration,
		String excelFileName,
		String configurationName,
		Map<String, Map<Integer, Integer>> premiumCountersForFile,
		AtomicBoolean simulatorFinished
	) {
		boolean isSlave = Boolean.parseBoolean(configuration.getProperty("simulation.slave", "false"));
		SEStats sEStats = getSEStats(configuration);
		Map<Integer, String> allPremiums = SEStats.allPremiums();
		AtomicInteger recordFounds = new AtomicInteger(0);
		AtomicInteger dataAggStoricoColIndex = new AtomicInteger(0);
		AtomicInteger fileColIndex = new AtomicInteger(0);
		AtomicReference<CellStyle> numberCellStyle = new AtomicReference<>();
		AtomicReference<CellStyle> dateCellStyle = new AtomicReference<>();
		readOrCreateExcel(
			excelFileName,
			workBook -> {
				Sheet sheet = workBook.getSheet("Risultati");
				recordFounds.set(workBook.getSheet("Risultati").getPhysicalNumberOfRows());
				dataAggStoricoColIndex.set(Shared.getCellIndex(sheet, DATA_AGGIORNAMENTO_STORICO_LABEL));
				fileColIndex.set(Shared.getCellIndex(sheet, FILE_LABEL));
			},
			null,
			null
		);
		List<Integer> excelRecords = IntStream.range(2, recordFounds.get()).boxed().collect(Collectors.toList());
		if (isSlave) {
			Collections.shuffle(excelRecords);
		}
		int rowProcessedCounter = 0;
		for (Integer rowIndex : excelRecords) {
			++rowProcessedCounter;
			AtomicReference<PersistentStorage> storageWrapper = new AtomicReference<>();
			if (simulatorFinished.get()) {
				setThreadPriorityToMax();
				int remainedRecords = (excelRecords.size() - (rowProcessedCounter + 1));
				if (remainedRecords % 100 == 0) {
					System.out.println("History update is going to finish: " + remainedRecords + " records remained");
				}
			}
			readOrCreateExcel(
				excelFileName,
				workBook -> {
					Sheet sheet = workBook.getSheet("Risultati");
					Row row = sheet.getRow(rowIndex);
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
				null
			);
			if (storageWrapper.get() != null) {
				Map<Integer, Integer> premiumCounters = premiumCountersForFile.computeIfAbsent(storageWrapper.get().getName(), key -> {
					PersistentStorage storage = storageWrapper.get();
					File premiumCountersFile = new File(storage.getAbsolutePathWithoutExtension() + "-historical-data.json");
					if (!premiumCountersFile.exists()) {
						return computePremiumCountersData(sEStats, storage, premiumCountersFile);
					} else {
						Map<String, Object> data = readPremiumCountersData(premiumCountersFile);
						if (LocalDate.parse(
							(String)data.get("referenceDate"),
							TimeUtils.defaultLocalDateFormat
							).compareTo(TimeUtils.toLocalDate(sEStats.getLatestExtractionDate())) < 0
						) {
							return computePremiumCountersData(sEStats, storage, premiumCountersFile);
						}
						return (Map<Integer, Integer>)data.get("premiumCounters");
					}
				});
				if (!isSlave) {
					readOrCreateExcel(
						excelFileName,
						workBook -> {
							Sheet sheet = workBook.getSheet("Risultati");
							if (rowIndex == 2) {
								numberCellStyle.set(workBook.createCellStyle());
								numberCellStyle.get().setAlignment(HorizontalAlignment.RIGHT);
								numberCellStyle.get().setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
								dateCellStyle.set(workBook.createCellStyle());
								dateCellStyle.get().setAlignment(HorizontalAlignment.CENTER);
								dateCellStyle.get().setDataFormat(workBook.createDataFormat().getFormat("dd/MM/yyyy"));
							} else {
								Row previousRow = sheet.getRow(rowIndex -1);
								dateCellStyle.set(previousRow.getCell(dataAggStoricoColIndex.get()).getCellStyle());
								numberCellStyle.set(
									previousRow.getCell(Shared.getCellIndex(sheet, getHistoryPremiumLabel(SEStats.allPremiumLabels().get(0)))).getCellStyle()
								);
							}
							Row row = sheet.getRow(rowIndex);
							if (storageWrapper.get().getName().equals(row.getCell(fileColIndex.get()).getStringCellValue())) {
								Cell dataAggStoricoCell = row.getCell(dataAggStoricoColIndex.get());
								for (Map.Entry<Integer, String> premiumData : allPremiums.entrySet()) {
									Cell cell = row.getCell(Shared.getCellIndex(sheet, getHistoryPremiumLabel(premiumData.getValue())));
									Integer premiumCounter = premiumCounters.get(premiumData.getKey());
									cell.setCellStyle(numberCellStyle.get());
									if (premiumCounter != null) {
										cell.setCellValue(premiumCounter.doubleValue());
									} else {
										cell.setCellValue(0d);
									}
								}
								Cell cell = row.getCell(Shared.getCellIndex(sheet, COSTO_STORICO_LABEL));
								cell.setCellStyle(numberCellStyle.get());
								cell.setCellValue(
									sEStats.getAllWinningCombos().size() * row.getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).getNumericCellValue()
								);
								dataAggStoricoCell.setCellStyle(dateCellStyle.get());
								dataAggStoricoCell.setCellValue(sEStats.getLatestExtractionDate());
							}
						},
						null,
						workBook -> {
							store(excelFileName, workBook);
							Row row = workBook.getSheet("Risultati").getRow(rowIndex);
							System.out.println(
								"Aggiornamento storico completato per " +
								TimeUtils.getDefaultDateFormat().format(row.getCell(0).getDateCellValue()) + " - " +
								row.getCell(fileColIndex.get()).getStringCellValue()
							);
						}
					);
				}
			}
		}
		String basePath = Optional.ofNullable(configuration.getProperty("simulation.group")).map(groupName -> {
			return PersistentStorage.buildWorkingPath(groupName);
		}).orElseGet(() -> PersistentStorage.buildWorkingPath());
		//Puliamo file json duplicati da google drive
		for (File file : ResourceUtils.INSTANCE.find("(1)", "json", basePath)) {
			file.delete();
		}
	}

	private static SEStats getSEStats(Properties configuration) {
		return SEStats.get(
			configuration.getProperty(
				"competition.archive.start-date",
				new SELotteryMatrixGeneratorEngine().getDefaultExtractionArchiveStartDate()
			), TimeUtils.defaultLocalDateFormat.format(LocalDate.now())
		);
	}

	private static Map<String, Object> readPremiumCountersData(File premiumCountersFile) {
		Map<String, Object> data = null;
		try {
			data = objectMapper.readValue(premiumCountersFile, Map.class);
			data.put("premiumCounters",((Map<String, Integer>)objectMapper.readValue(premiumCountersFile, Map.class).get("premiumCounters")).entrySet().stream()
				.collect(Collectors.toMap(entry -> Integer.parseInt(entry.getKey()), Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new)));
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
		return data;
	}

	private static Map<Integer, Integer> computePremiumCountersData(
		SEStats sEStats,
		PersistentStorage storage,
		File premiumCountersFile
	) {
		System.out.println("Computing historycal data of " + storage.getName());
		Map<String, Object> qualityCheckResult = sEStats.checkQuality(storage::iterator);
		System.out.println("Computed historycal data of " + storage.getName());
		Map<Integer, Integer> pC =
			(Map<Integer, Integer>)qualityCheckResult.get("premium.counters");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("premiumCounters", pC);
		data.put("referenceDate", qualityCheckResult.get("referenceDate"));
		writePremiumCountersData(premiumCountersFile, data);
		return pC;
	}

	private static void writePremiumCountersData(File premiumCountersFile, Map<String, Object> qualityCheckResult) {
		try {
			objectMapper.writeValue(premiumCountersFile, qualityCheckResult);
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
	}

	private static Function<LocalDate, Consumer<List<Storage>>> buildSystemProcessor(String excelFileName) {
		AtomicBoolean rowAddedFlag = new AtomicBoolean(false);
		AtomicBoolean fileCreatedFlag = new AtomicBoolean(false);
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
						store(excelFileName, workBook);
					}
				}
			);
		};
	}

	private static boolean addRowData(
		SimpleWorkbookTemplate workBookTemplate,
		LocalDate extractionDate,
		List<Storage> storages
	) throws UnsupportedEncodingException {
		Storage storage = !storages.isEmpty() ? storages.get(storages.size() -1) : null;
		if (storage != null) {
			Map<String, Integer> results = allTimeStats.check(extractionDate, storage::iterator);
			workBookTemplate.addCell(TimeUtils.toDate(extractionDate)).getCellStyle().setAlignment(HorizontalAlignment.CENTER);
			List<String> allPremiumLabels = SEStats.allPremiumLabels();
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
			List<String> allFormulas = SEStats.allPremiumLabels().stream().map(
				label -> generatePremiumFormula(sheet, currentRowNum, label, lb -> lb)).collect(Collectors.toList());
			String formula = String.join("+", allFormulas);
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			formula = generateSaldoFormula(currentRowNum, sheet, Arrays.asList(RITORNO_LABEL, COSTO_LABEL));
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			workBookTemplate.addCell(Collections.nCopies(SEStats.allPremiums().size(), null));
			workBookTemplate.addCell(0, "#,##0");
			cell.getCellStyle().setAlignment(HorizontalAlignment.CENTER);
			allFormulas = SEStats.allPremiumLabels().stream().map(
					label -> generatePremiumFormula(sheet, currentRowNum, label, LotteryMatrixSimulator::getHistoryPremiumLabel)).collect(Collectors.toList());
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
				URLEncoder.encode(persistentStorage.getName(), "UTF-8")
			);
			return true;
		}
		return false;
	}

	private static String generateSaldoFormula(int currentRowNum, Sheet sheet, List<String> labels) {
		return String.join("-", labels.stream().map(label ->
			"(" + CellReference.convertNumToColString(Shared.getCellIndex(sheet, label))  + currentRowNum + ")"
		).collect(Collectors.toList()));
	}

	private static String generatePremiumFormula(Sheet sheet, int currentRowNum, String columnLabel, UnaryOperator<String> transformer) {
		return "(" + CellReference.convertNumToColString(Shared.getCellIndex(sheet, transformer.apply(columnLabel))) + currentRowNum + "*" + SEStats.premiumPrice(columnLabel) + ")";
	}

	private static String getHistoryPremiumLabel(String label) {
		return "Totale " + label.toLowerCase() + " (storico)";
	}

	private static Function<LocalDate, Function<List<Storage>, Integer>> buildExtractionDatePredicate(
		String configurationName,
		String excelFileName,
		Integer redundant,
		AtomicInteger redundantCounter
	) {
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
					store(excelFileName, workBook)
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

	private static Integer checkAlreadyProcessed(
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

	private static void createWorkbook(Workbook workBook, String excelFileName) {
		SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
		Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati", true);

		List<String> summaryFormulas = new ArrayList<>();
		String columnName = CellReference.convertNumToColString(0);
		summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + allTimeStats.getAllWinningCombos().size() * 2 +")");
		for (int i = 1; i < excelHeaderLabels.size()-3; i++) {
			columnName = CellReference.convertNumToColString(i);
			summaryFormulas.add(
				"FORMULA_SUM(" + columnName + "3:"+ columnName + allTimeStats.getAllWinningCombos().size() * 2 +")"
			);
		}
		summaryFormulas.add(
			"FORMULA_TEXT((SUM(" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_STORICO_LABEL)) + "3:" +
			CellReference.convertNumToColString(excelHeaderLabels.indexOf(SALDO_STORICO_LABEL)) + allTimeStats.getAllWinningCombos().size() * 2 +
			")/" + CellReference.convertNumToColString(excelHeaderLabels.indexOf(COSTO_STORICO_LABEL)) + "2),\"###,00%\")");
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
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, RITORNO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_LABEL)).setCellStyle(headerNumberStyle);
		for (String label : SEStats.allPremiumLabels()) {
			sheet.getRow(1).getCell(Shared.getCellIndex(sheet, label)).setCellStyle(headerNumberStyle);
			sheet.getRow(1).getCell(Shared.getCellIndex(sheet, getHistoryPremiumLabel(label))).setCellStyle(headerNumberStyle);
		}
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_STORICO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, RITORNO_STORICO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_STORICO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, DATA_LABEL), 3800);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, COSTO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, RITORNO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, SALDO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, COSTO_STORICO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, RITORNO_STORICO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, SALDO_STORICO_LABEL), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, DATA_AGGIORNAMENTO_STORICO_LABEL), 3800);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, FILE_LABEL), 12000);
		//System.out.println(PersistentStorage.buildWorkingPath() + File.separator + excelFileName + " succesfully created");
	}

	private static void readOrCreateExcel(
		String excelFileName,
		ThrowingConsumer<Workbook, Throwable> action,
		ThrowingConsumer<Workbook, Throwable> createAction,
		ThrowingConsumer<Workbook, Throwable> finallyAction
	) {
		Synchronizer.INSTANCE.execute(excelFileName, () -> {
			Workbook workBook = null;
			try {
				try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)) {
					workBook = new XSSFWorkbook(inputStream);
					action.accept(workBook);
				} catch (FileNotFoundException exc) {
					if (createAction == null) {
						throw exc;
					}
					workBook = new XSSFWorkbook();
					createAction.accept(workBook);
				}
			} catch (Throwable exc) {
				throw new RuntimeException(exc);
			} finally {
				if (workBook != null) {
					if (finallyAction!= null) {
						try {
							finallyAction.accept(workBook);
						} catch (Throwable exc) {
							throw new RuntimeException(exc);
						}
					}
					try {
						workBook.close();
					} catch (IOException exc) {
						throw new RuntimeException(exc);
					}
				}
			}
		});
	}

	private static void store(String excelFileName, Workbook workBook) {
		Integer savingCounterForFile = savingOperationCounters.computeIfAbsent(excelFileName, key -> 0) + 1;
		File file = new File(PersistentStorage.buildWorkingPath() + File.separator + excelFileName);
		savingOperationCounters.put(excelFileName, savingCounterForFile);
		if (savingCounterForFile % 100 == 0) {
			backup(file);
		}
		try (OutputStream destFileOutputStream = new FileOutputStream(file)){
			BaseFormulaEvaluator.evaluateAllFormulaCells(workBook);
			workBook.write(destFileOutputStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void backup(File file) {
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
