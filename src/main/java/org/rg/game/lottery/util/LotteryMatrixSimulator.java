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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.CollectionUtils;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;
import org.rg.game.lottery.engine.Storage;
import org.rg.game.lottery.engine.TimeUtils;



public class LotteryMatrixSimulator {
	static Pattern regexForExtractConfigFileName = Pattern.compile("\\[.*?\\]\\[.*?\\]\\[.*?\\](.*)\\.txt");

	public static void main(String[] args) throws IOException {
		ZipSecureFile.setMinInflateRatio(0);
		Collection<CompletableFuture<Void>> futures = new ArrayList<>();
		execute("se", futures);
		execute("md", futures);
		futures.stream().forEach(CompletableFuture::join);
	}

	private static <L extends LotteryMatrixGeneratorAbstEngine> void execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) throws IOException {
		SEStats.forceLoadingFromExcel = false;
		SEStats.get("03/12/1997", TimeUtils.defaultDateFormat.format(new Date()));
		SEStats.get("02/07/2009", TimeUtils.defaultDateFormat.format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		Supplier<LotteryMatrixGeneratorAbstEngine> engineSupplier = SELotteryMatrixGeneratorEngine::new;
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
					simulationGroup = simulationGroup.replace("${localhost.name}", InetAddress.getLocalHost().getHostName());
					config.setProperty("simulation.group", simulationGroup);
					config.setProperty(
						"group",
						simulationGroup
					);
				}
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
			LotteryMatrixGeneratorAbstEngine engine = engineSupplier.get();
			String configFileName = configuration.getProperty("file.name").replace("." + configuration.getProperty("file.extension"), "");
			configuration.setProperty(
				"nameSuffix",
				configFileName
			);
			Collection<LocalDate> competitionDatesFlat = engine.computeExtractionDates(configuration.getProperty("competition"));
			String redundantConfigValue = configuration.getProperty("simulation.redundancy");
			cleanup(
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

	private static void cleanup(String excelFileName, Collection<LocalDate> competitionDates, String configFileName, Integer redundancy) {
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
				createExcelFile(workBook, excelFileName),
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
					createExcelFile(workBook, excelFileName),
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
		LotteryMatrixGeneratorAbstEngine engine, List<List<LocalDate>> competitionDates) {
		AtomicInteger redundantCounter = new AtomicInteger(0);
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
			String redundantConfigValue = configuration.getProperty("simulation.redundancy");
			engine.getExecutor().apply(
				buildExtractionDatePredicate(
					configuration.getProperty("nameSuffix"),
					excelFileName,
					redundantConfigValue != null? Integer.valueOf(redundantConfigValue) : null,
					redundantCounter
				)
			).apply(
				buildSystemProcessor(excelFileName)
			);
		}
	}

	private static Function<LocalDate, Consumer<List<Storage>>> buildSystemProcessor(String excelFileName) {
		return extractionDate -> storages -> {
			readOrCreateExcel(
				excelFileName,
				workBook -> {
					SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
					workBookTemplate.getOrCreateSheet("Risultati", true);
					while (true) {
						Row row = workBookTemplate.getCurrentRow();
						if (row == null || row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK) {
							addRowData(workBookTemplate, extractionDate, storages);
							break;
						} else {
							workBookTemplate.addRow();
						}
					}
				},
				workBook ->
					createExcelFile(workBook, excelFileName),
				workBook -> {
					SEStats.clear();
					store(excelFileName, workBook);
				}
			);
		};
	}

	private static void addRowData(
		SimpleWorkbookTemplate workBookTemplate,
		LocalDate extractionDate,
		List<Storage> storages
	) throws UnsupportedEncodingException {
		Storage storage = !storages.isEmpty() ? storages.get(storages.size() -1) : null;
		if (storage != null) {
			Map<String, Integer> results = Shared.getSEStats().check(extractionDate, storage::iterator);
			workBookTemplate.addCell(TimeUtils.toDate(extractionDate)).getCellStyle().setAlignment(HorizontalAlignment.CENTER);
			List<String> allPremiumLabels = Shared.allPremiumLabels();
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
			String formula = "(B" + currentRowNum + "*" + SEStats.premiumPrice(2) + ")+" + "(C" + currentRowNum + "*" + SEStats.premiumPrice(3) + ")+"
				 + "(D" + currentRowNum + "*" + SEStats.premiumPrice(4) + ")+" + "(E" + currentRowNum + "*" + SEStats.premiumPrice(5) + ")+"
				 + "(F" + currentRowNum + "*" + SEStats.premiumPrice(6) + ")";
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			formula = "(H"  + currentRowNum + ")-(G" + currentRowNum + ")";
			workBookTemplate.addFormulaCell(formula, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
			workBookTemplate.addCell(Collections.nCopies(9, null));
			Cell cellName = workBookTemplate.addCell(storage.getName()).get(0);
			if (storage instanceof PersistentStorage) {
				PersistentStorage persistentStorage = (PersistentStorage)storage;
				workBookTemplate.setLinkForCell(
					HyperlinkType.FILE,
					cellName,
					URLEncoder.encode(persistentStorage.getName(), "UTF-8")
				);
			}
		}
	}

	private static Function<LocalDate, Function<List<Storage>, Integer>> buildExtractionDatePredicate(
		String configurationName,
		String excelFileName,
		Integer redundant,
		AtomicInteger redundantCounter
	) {
		return extractionDate -> storages -> {
			if (TimeUtils.defaultLocalDateFormatter.format(extractionDate).equals("25/07/2009")) {
				System.out.println(TimeUtils.defaultLocalDateFormatter.format(extractionDate) + redundantCounter.get());
			}
			AtomicReference<Integer> checkResult = new AtomicReference<Integer>();
			readOrCreateExcel(
				excelFileName,
				workBook ->
					checkResult.set(openExcelFile(workBook, configurationName, extractionDate)),
				workBook ->
					createExcelFile(workBook, excelFileName)
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

	private static Integer openExcelFile(
		Workbook workBook,
		String configurationName,
		LocalDate extractionDate
	) throws IOException {
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

	private static void createExcelFile(Workbook workBook, String excelFileName) {
		SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
		Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati", true);
		List<String> labels = new ArrayList<>();
		labels.add("Data");
		List<String> allPremiumLabels = Shared.allPremiumLabels();
		labels.addAll(allPremiumLabels);
		labels.add("Costo");
		labels.add("Ritorno");
		labels.add("Saldo");
		labels.addAll(allPremiumLabels.stream().map(label -> label + " (storico)").collect(Collectors.toList()));
		labels.add("Costo (storico)");
		labels.add("Ritorno (storico)");
		labels.add("Saldo (storico)");
		labels.add("Data agg. storico");
		List<String> summaryFormulas = new ArrayList<>();
		String columnName = SimpleWorkbookTemplate.getLetterAtIndex(0);
		summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")");
		for (int i = 1; i < labels.size()-4; i++) {
			columnName = SimpleWorkbookTemplate.getLetterAtIndex(i);
			summaryFormulas.add(
				"FORMULA_SUM(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")"
			);
		}
		labels.add("File");
		summaryFormulas.add("");
		summaryFormulas.add("");
		summaryFormulas.add("");
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
		headerNumberStyle.cloneStyleFrom(sheet.getRow(1).getCell(labels.size()-4).getCellStyle());
		headerNumberStyle.setDataFormat(workBook.createDataFormat().getFormat("#,##0"));
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, "Costo")).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, "Ritorno")).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, "Saldo")).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, "Costo (storico)")).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, "Ritorno (storico)")).setCellStyle(headerNumberStyle);
		sheet.getRow(1).getCell(Shared.getCellIndex(sheet, "Saldo (storico)")).setCellStyle(headerNumberStyle);
		sheet.setColumnWidth(0, 3800);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "Costo"), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "Ritorno"), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "Saldo"), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "Costo (storico)"), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "Ritorno (storico)"), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "Saldo (storico)"), 3000);
		sheet.setColumnWidth(Shared.getCellIndex(sheet, "File"), 12000);
		//workBookTemplate.setAutoFilter(1, Shared.getSEStats().getAllWinningCombos().size() * 2, 0, labels.size() - 1);
		//System.out.println(PersistentStorage.buildWorkingPath() + File.separator + excelFileName + " succesfully created");
	}

	private static void readOrCreateExcel(
		String excelFileName,
		ThrowingConsumer<Workbook, Throwable> action,
		ThrowingConsumer<Workbook, Throwable> createAction,
		ThrowingConsumer<Workbook, Throwable> finallyAction
	) {
		synchronized (LotteryMatrixSimulator.class) {
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
		}
	}

	private static void store(String excelFileName, Workbook workBook) {
		synchronized(LotteryMatrixSimulator.class) {
			try (OutputStream destFileOutputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)){
				BaseFormulaEvaluator.evaluateAllFormulaCells(workBook);
				workBook.write(destFileOutputStream);
				workBook.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
