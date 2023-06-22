package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.LogUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;

public class SESimulationSummaryGenerator {
	static final String EXTRACTION_COUNTER_LABEL = "Conteggio estrazioni";
	static final String SYSTEM_COUNTER_LABEL = "Conteggio sistemi";

	static {
		ZipSecureFile.setMinInflateRatio(0);
	}

	public static void main(String[] args) {
		try {
			String simulationSummaryFolder = Arrays.stream(
				ResourceUtils.INSTANCE.pathsFromSystemEnv(
						"working-path.simulations.folder"
					)
				).findFirst().orElseGet(() -> null);
			LogUtils.info("\n\n");
			String simulationSummaryFile = simulationSummaryFolder + File.separator + "Summary.xlsx";
			SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(true);
			Sheet summarySheet = workBookTemplate.getOrCreateSheet("Riepilogo", true);
			List<String> headerLabels = new ArrayList<>();
			List<String> headerLabelsTemp = new ArrayList<>(SELotterySimpleSimulator.reportHeaderLabels);
			List<String> headersToBeSkipped = new ArrayList<>(
				Arrays.asList(
					SELotterySimpleSimulator.HISTORICAL_UPDATE_DATE_LABEL,
					SELotterySimpleSimulator.FILE_LABEL
				)
			);
			headerLabelsTemp.removeAll(headersToBeSkipped);
			headerLabels.add(SELotterySimpleSimulator.FILE_LABEL);
			headerLabels.addAll(headerLabelsTemp);
			headerLabels.set(headerLabels.indexOf(SELotterySimpleSimulator.EXTRACTION_DATE_LABEL), SYSTEM_COUNTER_LABEL);
			headerLabels.add(headerLabels.indexOf(SYSTEM_COUNTER_LABEL), EXTRACTION_COUNTER_LABEL);
			headersToBeSkipped.add(SELotterySimpleSimulator.EXTRACTION_DATE_LABEL);
			workBookTemplate.createHeader(true, headerLabels);
			AtomicInteger reportCounter = new AtomicInteger(0);
			process(
				null,
				simulationSummaryFolder,
				workBookTemplate,
				headersToBeSkipped,
				reportCounter
			);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.FILE_LABEL), 15000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.FOLLOWING_PROGRESSIVE_HISTORICAL_COST_LABEL), 3000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.FOLLOWING_PROGRESSIVE_HISTORICAL_RETURN_LABEL), 3000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.HISTORICAL_COST_LABEL), 3000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.HISTORICAL_RETURN_LABEL), 3000);
			try (OutputStream destFileOutputStream = new FileOutputStream(simulationSummaryFile)){
				workBookTemplate.addSheetConditionalFormatting(
					new int[] {
						Shared.getCellIndex(summarySheet, Premium.LABEL_FIVE),
						Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_FIVE))
					},
					IndexedColors.YELLOW,
					ComparisonOperator.GT,
					"0"
				);
				workBookTemplate.addSheetConditionalFormatting(
					new int[] {
						Shared.getCellIndex(summarySheet, Premium.LABEL_FIVE_PLUS),
						Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_FIVE_PLUS))
					},
					IndexedColors.ORANGE,
					ComparisonOperator.GT,
					"0"
				);
				workBookTemplate.addSheetConditionalFormatting(
					new int[] {
						Shared.getCellIndex(summarySheet, Premium.LABEL_SIX),
						Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.getFollowingProgressiveHistoricalPremiumLabel(Premium.LABEL_SIX))
					},
					IndexedColors.RED,
					ComparisonOperator.GT,
					"0"
				);
				workBookTemplate.setAutoFilter(0, reportCounter.get(), 0, headerLabels.size() - 1);
				BaseFormulaEvaluator.evaluateAllFormulaCells(workBookTemplate.getWorkbook());
				workBookTemplate.getWorkbook().write(destFileOutputStream);
			}
			LogUtils.info("\n\nSummary file succesfully generated");
		} catch (Throwable exc) {
			LogUtils.error("\n\nUnable to generate summary file");
			exc.printStackTrace();
		}
	}

	protected static void process(
		String currentRelativePath,
		String folderAbsolutePath,
		SimpleWorkbookTemplate workBookTemplate,
		List<String> headersToBeSkipped,
		AtomicInteger reportCounter
	) {
		for (File singleSimFolder : new File(folderAbsolutePath).listFiles(
			(file, name) -> {
				File currentIteratedFile = new File(file, name);
				return currentIteratedFile.isDirectory() &&
					!currentIteratedFile.getName().equals(SELotterySimpleSimulator.DATA_FOLDER_NAME) &&
					!currentIteratedFile.getName().equals(SELotteryComplexSimulator.GENERATED_FOLDER_NAME);
			})
		) {
			String singleSimFolderRelPath = Optional.ofNullable(currentRelativePath).map(cRP -> cRP + "/").orElseGet(() -> "") + singleSimFolder.getName();
			LogUtils.info("Scanning " + singleSimFolder.getAbsolutePath());
			File report = Arrays.stream(singleSimFolder.listFiles((file, name) -> name.endsWith("report.xlsx"))).findFirst().orElseGet(() -> null);
			if (report != null) {
				reportCounter.incrementAndGet();
				try {
					process(
						singleSimFolderRelPath,
						workBookTemplate,
						headersToBeSkipped,
						singleSimFolder,
						report
					);
				} catch (Throwable exc) {
					LogUtils.warn("Unable to process " + report.getAbsolutePath() + ": " + exc.getMessage());
					List<File> backupFiles = ResourceUtils.INSTANCE.findReverseOrdered("report - ", "xlsx", report.getParentFile().getAbsolutePath());
					boolean processed = false;
					if (backupFiles.size() > 0) {
						LogUtils.info("Trying to process its backups");
						for (File backup : backupFiles) {
							try {
								process(
									singleSimFolderRelPath,
									workBookTemplate,
									headersToBeSkipped,
									singleSimFolder,
									backup
								);
								processed = true;
								break;
							} catch (Throwable e) {

							}
						}
					}
					if (!processed) {
						LogUtils.error("Unable to process backups of " + report.getAbsolutePath());
					}
				}
			}
			process(
				singleSimFolderRelPath,
				singleSimFolder.getAbsolutePath(),
				workBookTemplate,
				headersToBeSkipped,
				reportCounter
			);
		}
	}

	protected static void process(
		String singleSimFolderRelPath,
		SimpleWorkbookTemplate summaryWorkBookTemplate,
		List<String> headersToBeSkipped,
		File singleSimFolder,
		File report
	) throws IOException {
		try (InputStream inputStream = new FileInputStream(report.getAbsolutePath());Workbook simulationWorkBook = new XSSFWorkbook(inputStream);) {
			FormulaEvaluator evaluator = simulationWorkBook.getCreationHelper().createFormulaEvaluator();
			Sheet resultSheet = simulationWorkBook.getSheet("Risultati");
			summaryWorkBookTemplate.addRow();
			Cell cellForName = summaryWorkBookTemplate.addCell(report.getName()).get(0);
			summaryWorkBookTemplate.setLinkForCell(
				HyperlinkType.FILE,
				cellForName,
				singleSimFolderRelPath + "/" + report.getName()
			);
			Set<Date> extractionDatesHolder = new LinkedHashSet<>();
			int generatedSystemCounter = 0;
			for (int i = SELotterySimpleSimulator.getHeaderSize(); i < resultSheet.getLastRowNum() + 1; i++) {
				generatedSystemCounter++;
				extractionDatesHolder.add(resultSheet.getRow(i).getCell(0).getDateCellValue());
			}
			summaryWorkBookTemplate.addCell(extractionDatesHolder.size(), "#,##0");
			summaryWorkBookTemplate.addCell(generatedSystemCounter, "#,##0");
			for (String cellLabel : SELotterySimpleSimulator.reportHeaderLabels) {
				if (!headersToBeSkipped.contains(cellLabel)) {
					Cell simulationCell = resultSheet.getRow(1).getCell(Shared.getCellIndex(resultSheet, cellLabel));
					CellValue simulationCellValue = evaluator.evaluate(simulationCell);
					if (simulationCellValue.getCellType().equals(CellType.NUMERIC)) {
						summaryWorkBookTemplate.addCell(simulationCellValue.getNumberValue(), "#,##0");
					} else if (simulationCellValue.getCellType().equals(CellType.STRING)) {
						summaryWorkBookTemplate.addCell(simulationCellValue.getStringValue()).stream().findFirst().get().getCellStyle().setAlignment(HorizontalAlignment.RIGHT);
					}
				}
			}
		}
	}

}
