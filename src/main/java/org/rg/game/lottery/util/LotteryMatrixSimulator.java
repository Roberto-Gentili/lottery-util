package org.rg.game.lottery.util;

import java.io.File;
import java.io.FileInputStream;
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
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;
import org.rg.game.lottery.engine.Storage;



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
		SEStats.get("03/12/1997", Shared.standardDatePattern.format(new Date()));
		SEStats.get("02/07/2009", Shared.standardDatePattern.format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		Supplier<LotteryMatrixGeneratorAbstEngine> engineSupplier = SELotteryMatrixGeneratorEngine::new;
		Collection<FileSystemItem> configurationFiles = new TreeSet<>((fISOne, fISTwo) -> {
			return fISOne.getName().compareTo(fISTwo.getName());
		});
		configurationFiles.addAll(FileSystemItem.ofPath(
			PersistentStorage.buildWorkingPath()).findInChildren(
				FileSystemItem.Criteria.forAllFileThat(file -> file.getName().contains("-matrix-generator"))
			)
		);
		configurationFiles.addAll(
			ComponentContainer.getInstance().getPathHelper().findResources(absolutePath -> {
				return absolutePath.contains(configFilePrefix + "-matrix-generator");
			})
		);
		List<Properties> configurations = new ArrayList<>();
		for (FileSystemItem fIS : configurationFiles) {
			try (InputStream configIS = fIS.toInputStream()) {
				Properties config = new Properties();
				config.load(configIS);
				if (Boolean.parseBoolean(config.getProperty("enabled", "false"))) {
					config.setProperty("file.name", fIS.getName());
					config.setProperty("file.parent.absolutePath", fIS.getParent().getAbsolutePath());
					config.setProperty("file.extension", fIS.getExtension());
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
			engine.setup(configuration);
			if (Boolean.parseBoolean(configuration.getProperty("async", "false"))) {
				futures.add(
					CompletableFuture.runAsync(
						() ->
						process(
							excelFileName,
							workbook -> {
								engine.getExecutor().apply(buildExtractionDatePredicate(workbook)).apply(buildSystemProcessor(workbook));
							}
						)
					)
				);
			} else {
				process(
					excelFileName,
					workbook ->
						engine.getExecutor().apply(buildExtractionDatePredicate(workbook)).apply(buildSystemProcessor(workbook))

				);
			}


		}
	}

	private static Consumer<Storage> buildSystemProcessor(SimpleWorkbookTemplate workBookTemplate) {
		return storage -> {

		};
	}

	private static Predicate<LocalDate> buildExtractionDatePredicate(SimpleWorkbookTemplate workBookTemplate) {
		return extractionDate -> {
			Iterator<Row> rowIterator = workBookTemplate.getWorkbook().getSheet("Risultati").rowIterator();
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				Cell data = row.getCell(0);
				try {
					if (data != null && Shared.formatter.format(extractionDate).equals(data)) {
						return false;
					}
				} catch (Throwable exc) {
					exc.printStackTrace();
				}
			}
			return true;
		};
	}

	private static void process(String excelFileAbsolutePath, Consumer<SimpleWorkbookTemplate> processor) {
		Workbook workBook = null;
		try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileAbsolutePath);) {
			workBook = new XSSFWorkbook(inputStream);
			SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
			processor.accept(workBookTemplate);
		} catch (IOException exc) {
			workBook = new XSSFWorkbook();
			SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(workBook);
			workBookTemplate.getOrCreateSheet("Risultati", true);
			List<String> labels = new ArrayList<>();
			labels.add("Data");
			labels.addAll(Shared.allPremiumLabels());
			List<String> summaryFormulas = new ArrayList<>();
			summaryFormulas.add("");
			for (int i = 1; i < labels.size(); i++) {
				String columnName = Shared.getLetterAtIndex(i);
				summaryFormulas.add(
					"FORMULA_SUM(" + columnName + "3:"+ columnName + Shared.getSEStats().getAllWinningCombos().size() * 2 +")"
				);
			}
			workBookTemplate.createHeader("Risultati", true, Arrays.asList(
				labels,
				summaryFormulas
			));
			processor.accept(workBookTemplate);
		} finally {
			try (OutputStream destFileOutputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator + excelFileAbsolutePath)){
				workBook.write(destFileOutputStream);
				workBook.close();
			} catch (IOException exc) {
				throw new RuntimeException(exc);
			}
		}
	}

}
