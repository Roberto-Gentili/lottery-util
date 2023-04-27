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
		try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)) {
			Workbook workBook = new XSSFWorkbook(inputStream);
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
		} catch (FileNotFoundException e) {

		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}

	}

	private static void cleanupRedundant(String excelFileName, String configFileName, Integer redundancy) {
		Workbook workBook = null;
		if (redundancy != null) {
			try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)) {
				workBook = new XSSFWorkbook(inputStream);
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
			} catch (FileNotFoundException e) {

			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			if (workBook != null) {
				store(excelFileName, workBook);
			}
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
			Workbook workBook = null;
			try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName);) {
				workBook = new XSSFWorkbook(inputStream);
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
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			store(excelFileName, workBook);
			SEStats.clear();
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
			Integer checkResult = null;
			Workbook workBook = null;
			try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)) {
				workBook = new XSSFWorkbook(inputStream);
				Iterator<Row> rowIterator = workBook.getSheet("Risultati").rowIterator();
				rowIterator.next();
				rowIterator.next();
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					if (rowRefersTo(row, configurationName)) {
						Cell data = row.getCell(0);
						if (data != null && extractionDate.compareTo(TimeUtils.toLocalDate(data.getDateCellValue())) == 0) {
							checkResult = -1;
							break;
						}
					}
				}
			} catch (FileNotFoundException exc) {
				workBook = new XSSFWorkbook();
				SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
				Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati", true);
				List<String> labels = new ArrayList<>();
				labels.add("Data");
				labels.addAll(Shared.allPremiumLabels());
				labels.add("Costo");
				labels.add("Ritorno");
				labels.add("Saldo");
				List<String> summaryFormulas = new ArrayList<>();
				String columnName = Shared.getLetterAtIndex(0);
				summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")");
				for (int i = 1; i < labels.size(); i++) {
					columnName = Shared.getLetterAtIndex(i);
					summaryFormulas.add(
						"FORMULA_SUM(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")"
					);
				}
				labels.add("File");
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
				sheet.getRow(1).getCell(labels.size()-4).setCellStyle(headerNumberStyle);
				sheet.getRow(1).getCell(labels.size()-3).setCellStyle(headerNumberStyle);
				sheet.getRow(1).getCell(labels.size()-2).setCellStyle(headerNumberStyle);;
				sheet.setColumnWidth(0, 3800);
				sheet.setColumnWidth(labels.size()-4, 4500);
				sheet.setColumnWidth(labels.size()-3, 4500);
				sheet.setColumnWidth(labels.size()-2, 4500);
				sheet.setColumnWidth(labels.size()-1, 12000);
				workBookTemplate.setAutoFilter(1, Shared.getSEStats().getAllWinningCombos().size() * 2, 0, labels.size() - 1);
				store(excelFileName, workBook);
				System.out.println(PersistentStorage.buildWorkingPath() + File.separator + excelFileName + " succesfully created");
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			if (redundant != null) {
				Integer redundantCounterValue = redundantCounter.getAndIncrement();
				if (redundantCounterValue.intValue() > 0) {
					if (redundantCounterValue.intValue() < redundant) {
						checkResult =
							checkResult != null ?
								checkResult :
								0;
					} else {
						redundantCounter.set(1);
					}
				}
			}
			if (checkResult == null) {
				checkResult = 1;
			}
			return checkResult;
		};
	}

	private static void store(String excelFileName, Workbook workBook) {
		try (OutputStream destFileOutputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)){
			BaseFormulaEvaluator.evaluateAllFormulaCells(workBook);
			workBook.write(destFileOutputStream);
			workBook.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
