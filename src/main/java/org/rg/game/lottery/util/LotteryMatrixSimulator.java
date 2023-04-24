package org.rg.game.lottery.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
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

	public static void main(String[] args) throws IOException {
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
			String excelFileName = configuration.getProperty("file.name").replace("." + configuration.getProperty("file.extension"), "") + "-sim.xlsx";
			LotteryMatrixGeneratorAbstEngine engine = engineSupplier.get();
			configuration.setProperty("nameSuffix", configuration.getProperty("file.name")
					.replace("." + configuration.getProperty("file.extension"), ""));
			List<List<LocalDate>> competitionDates =
				CollectionUtils.toSubLists(
					new ArrayList<>(engine.computeExtractionDates(configuration.getProperty("competition"))),
					10
				);
			if (Boolean.parseBoolean(configuration.getProperty("async", "false"))) {
				futures.add(
					CompletableFuture.runAsync(
						() -> {
							process(configuration, excelFileName, engine, competitionDates);
						}
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

	private static void process(Properties configuration, String excelFileName,
			LotteryMatrixGeneratorAbstEngine engine, List<List<LocalDate>> competitionDates) {
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
			engine.getExecutor().apply(buildExtractionDatePredicate(excelFileName)).apply(buildSystemProcessor(excelFileName));
		}
	}

	private static Function<LocalDate, Consumer<Storage>> buildSystemProcessor(String excelFileName) {
		return extractionDate -> storage -> {
			Workbook workBook = null;
			try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName);) {
				workBook = new XSSFWorkbook(inputStream);
				SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
				workBookTemplate.getOrCreateSheet("Risultati", true);
				while (true) {
					Row row = workBookTemplate.getCurrentRow();
					if (row == null || row.getCell(0) == null || row.getCell(0).getCellType() == CellType.BLANK) {
						addRowData(workBookTemplate, extractionDate, storage);
						break;
					} else {
						workBookTemplate.addRow();
					}
				}
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			} finally {
				storage.delete();
			}
			try (OutputStream destFileOutputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileName)){
				SEStats.clear();
				workBook.write(destFileOutputStream);
				workBook.close();
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
		};
	}

	private static void addRowData(SimpleWorkbookTemplate workBookTemplate, LocalDate extractionDate, Storage storage) {
		Map<String, Integer> results = Shared.getSEStats().check(extractionDate, storage::iterator);
		workBookTemplate.addCell(TimeUtils.toDate(extractionDate));
		List<String> allPremiumLabels = Shared.allPremiumLabels();
		for (int i = 0; i < allPremiumLabels.size();i++) {
			Integer result = results.get(allPremiumLabels.get(i));
			if (result == null) {
				result = 0;
			}
			workBookTemplate.addCell(result, "#,##0").getCellStyle().setAlignment(HorizontalAlignment.CENTER);
		}
	}

	private static Predicate<LocalDate> buildExtractionDatePredicate(String excelFileAbsolutePath) {
		return extractionDate -> {
			Workbook workBook = null;
			try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileAbsolutePath)) {
				workBook = new XSSFWorkbook(inputStream);
				Iterator<Row> rowIterator = workBook.getSheet("Risultati").rowIterator();
				rowIterator.next();
				rowIterator.next();
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					Cell data = row.getCell(0);
					if (data != null && extractionDate.compareTo(TimeUtils.toLocalDate(data.getDateCellValue())) == 0) {
						return false;
					}
				}
			} catch (FileNotFoundException exc) {
				workBook = new XSSFWorkbook();
				SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
				Sheet sheet = workBookTemplate.getOrCreateSheet("Risultati", true);
				List<String> labels = new ArrayList<>();
				labels.add("Data");
				labels.addAll(Shared.allPremiumLabels());
				List<String> summaryFormulas = new ArrayList<>();
				String columnName = Shared.getLetterAtIndex(0);
				summaryFormulas.add("FORMULA_COUNTA(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")");
				for (int i = 1; i < labels.size(); i++) {
					columnName = Shared.getLetterAtIndex(i);
					summaryFormulas.add(
						"FORMULA_SUM(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")"
					);
				}
				workBookTemplate.createHeader("Risultati", true, Arrays.asList(
					labels,
					summaryFormulas
				));
				sheet.setColumnWidth(0, 3800);
				try (OutputStream destFileOutputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileAbsolutePath)){
					workBook.write(destFileOutputStream);
					workBook.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
			return true;
		};
	}
}
