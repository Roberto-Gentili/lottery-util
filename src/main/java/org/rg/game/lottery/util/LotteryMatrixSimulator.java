package org.rg.game.lottery.util;

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
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
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
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.CollectionUtils;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;
import org.rg.game.lottery.engine.Storage;
import org.rg.game.lottery.engine.TimeUtils;

import com.fasterxml.jackson.databind.ObjectMapper;



public class LotteryMatrixSimulator {
	private static final String SALDO_LABEL = "Saldo";
	private static final String RITORNO_LABEL = "Ritorno";
	private static final String COSTO_LABEL = "Costo";
	private static final String DATA_LABEL = "Data";
	private static final String SALDO_STORICO_LABEL = "Saldo (storico)";
	private static final String RITORNO_STORICO_LABEL = "Ritorno (storico)";
	private static final String COSTO_STORICO_LABEL = "Costo (storico)";
	private static final String FILE_LABEL = "File";
	private static final String DATA_AGGIORNAMENTO_STORICO_LABEL = "Data agg. storico";
	private static final ObjectMapper objectMapper = new ObjectMapper();
	static Pattern regexForExtractConfigFileName = Pattern.compile("\\[.*?\\]\\[.*?\\]\\[.*?\\](.*)\\.txt");
	static String hostName;
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
		SEStats.get("03/12/1997", TimeUtils.defaultDateFormat.format(new Date()));
		SEStats.get("02/07/2009", TimeUtils.defaultDateFormat.format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		Supplier<SELotteryMatrixGeneratorEngine> engineSupplier = SELotteryMatrixGeneratorEngine::new;
		Collection<FileSystemItem> configurationFiles = new TreeSet<>((fISOne, fISTwo) -> {
			return fISOne.getName().compareTo(fISTwo.getName());
		});
		configurationFiles.addAll(FileSystemItem.ofPath(
			PersistentStorage.buildWorkingPath()).findInChildren(
				FileSystemItem.Criteria.forAllFileThat(file -> file.getName().contains("-matrix-generator") && file.getExtension().equals("properties"))
			)
		);
		configurationFiles.addAll(
			ComponentContainer.getInstance().getPathHelper().findResources(absolutePath -> {
				return absolutePath.contains(configFilePrefix + "-matrix-generator") && absolutePath.endsWith("properties");
			})
		);
		List<Properties> configurations = new ArrayList<>();
		for (FileSystemItem fIS : configurationFiles) {
			try (InputStream configIS = fIS.toInputStream()) {
				Properties config = new Properties();
				config.load(configIS);
				config.setProperty("file.name", fIS.getName());
				config.setProperty("file.parent.absolutePath", fIS.getParent().getAbsolutePath());
				config.setProperty("file.extension", fIS.getExtension());
				String simulationDates = config.getProperty("simulation.dates");
				if (simulationDates != null) {
					config.setProperty("competition", simulationDates);
				}
				if (Boolean.parseBoolean(config.getProperty("enabled", "false"))) {
					configurations.add(config);
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
			}
		}
		for (Properties configuration : configurations) {
			System.out.println(
				"Processing file '" + configuration.getProperty("file.name") + "' located in '" + configuration.getProperty("file.parent.absolutePath") + "'"
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

	private static void cleanup(Properties configuration, String excelFileName, Collection<LocalDate> competitionDates, String configFileName, Integer redundancy) {
		Iterator<LocalDate> datesIterator = competitionDates.iterator();
		SEStats sEStats = getSEStats(configuration);
		LocalDate latestExtractionArchiveStartDate = TimeUtils.toLocalDate(sEStats.getLatestExtractionDate());
		while (datesIterator.hasNext()) {
			if (datesIterator.next().compareTo(latestExtractionArchiveStartDate) > 0) {
				datesIterator.remove();
			}
		}
		cleanupRedundant(excelFileName, configFileName, redundancy);
		readOrCreateExcel(
			excelFileName,
			workBook -> {
				Iterator<Row> rowIterator = workBook.getSheet("Risultati").rowIterator();
				rowIterator.next();
				rowIterator.next();
				int initialSize = competitionDates.size();
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
				System.out.println(competitionDates.size() + " dates will be processed, " + (initialSize - competitionDates.size()) + " already processed");
			},
			workBook ->
				createWorkbook(workBook, excelFileName),
			workBook ->
				store(excelFileName, workBook)
		);
	}

	private static void cleanupRedundant(String excelFileName, String configFileName, Integer redundancy) {
		if (redundancy != null) {
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
					for (List<Row> rows : groupedForRedundancyRows.values()) {
						if (rows.size() < redundancy) {
							for (Row row : rows) {
								sheet.removeRow(row);
							}
						}
					}
				},
				workBook ->
					createWorkbook(workBook, excelFileName),
				workBook ->
					store(excelFileName, workBook)
			);
		}
	}

	private static boolean rowRefersTo(Row row, String configurationName) {
		Matcher matcher = regexForExtractConfigFileName.matcher(
			row.getCell(row.getLastCellNum()-1).getStringCellValue()
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
					datesToBeProcessed.stream().map(TimeUtils.defaultLocalDateFormatter::format).collect(Collectors.toList())
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
			if (!isSlave) {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
			}
			Map<String, Map<Integer, Integer>> premiumCountersForFile = new LinkedHashMap<>();
			while (!simulatorFinished.get()) {
				historyUpdateTaskStarted.set(true);
				updateHistory(configuration, excelFileName, configurationName, premiumCountersForFile);
			}
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			updateHistory(configuration, excelFileName, configurationName, premiumCountersForFile);
		});

	}

	private static void updateHistory(
		Properties configuration,
		String excelFileName,
		String configurationName,
		Map<String, Map<Integer, Integer>> premiumCountersForFile
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
		for (Integer rowIndex : excelRecords) {
			AtomicReference<PersistentStorage> storageWrapper = new AtomicReference<>();
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
							TimeUtils.defaultLocalDateFormatter
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
								TimeUtils.defaultDateFormat.format(row.getCell(0).getDateCellValue()) + " - " +
								row.getCell(fileColIndex.get()).getStringCellValue()
							);
						}
					);
				}
			}
		}
	}

	private static SEStats getSEStats(Properties configuration) {
		SEStats sEStats = SEStats.get(
			configuration.getProperty(
				"competition.archive.start-date",
				new SELotteryMatrixGeneratorEngine().getDefaultExtractionArchiveStartDate()
			), TimeUtils.defaultLocalDateFormatter.format(LocalDate.now())
		);
		return sEStats;
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
			Map<String, Integer> results = Shared.getSEStats().check(extractionDate, storage::iterator);
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
		List<String> labels = new ArrayList<>();
		labels.add(DATA_LABEL);
		Collection<String> allPremiumLabels = SEStats.allPremiumLabels();
		labels.addAll(allPremiumLabels);
		labels.add(COSTO_LABEL);
		labels.add(RITORNO_LABEL);
		labels.add(SALDO_LABEL);
		List<String> historyLabels = allPremiumLabels.stream().map(label -> getHistoryPremiumLabel(label)).collect(Collectors.toList());
		labels.addAll(historyLabels);
		labels.add(COSTO_STORICO_LABEL);
		labels.add(RITORNO_STORICO_LABEL);
		labels.add(SALDO_STORICO_LABEL);
		labels.add(DATA_AGGIORNAMENTO_STORICO_LABEL);
		List<String> summaryFormulas = new ArrayList<>();
		String columnName = CellReference.convertNumToColString(0);
		summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")");
		for (int i = 1; i < labels.size()-1; i++) {
			columnName = CellReference.convertNumToColString(i);
			summaryFormulas.add(
				"FORMULA_SUM(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")"
			);
		}
		labels.add(FILE_LABEL);
		summaryFormulas.add("");
		summaryFormulas.add("");
		workBookTemplate.createHeader(
			"Risultati",
			true,
			Arrays.asList(
				labels,
				summaryFormulas
			)
		);
		CellStyle headerNumberStyle = workBook.createCellStyle();
		headerNumberStyle.cloneStyleFrom(sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).getCellStyle());
		headerNumberStyle.setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, COSTO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, RITORNO_LABEL)).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, SALDO_LABEL)).setCellStyle(headerNumberStyle);
		for (String label : historyLabels) {
			sheet.getRow(1).getCell(Shared.getCellIndex(sheet, label)).setCellStyle(headerNumberStyle);
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
		//workBookTemplate.setAutoFilter(1, Shared.getSEStats().getAllWinningCombos().size() * 2, 0, labels.size() - 1);
		//System.out.println(PersistentStorage.buildWorkingPath() + File.separator + excelFileName + " succesfully created");
	}

	private static void readOrCreateExcel(
		String excelFileName,
		ThrowingConsumer<Workbook, Throwable> action,
		ThrowingConsumer<Workbook, Throwable> createAction,
		ThrowingConsumer<Workbook, Throwable> finallyAction
	) {
		StaticComponentContainer.Synchronizer.execute(excelFileName, () -> {
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
		StaticComponentContainer.Synchronizer.execute(excelFileName, () -> {
			try (OutputStream destFileOutputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)){
				BaseFormulaEvaluator.evaluateAllFormulaCells(workBook);
				workBook.write(destFileOutputStream);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
}
