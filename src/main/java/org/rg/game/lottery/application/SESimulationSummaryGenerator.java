package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.LogUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SimpleWorkbookTemplate;

public class SESimulationSummaryGenerator {

	public static void main(String[] args) {
		try {
			String simulationSummaryFolder = Arrays.stream(
				Shared.pathsFromSystemEnv(
						"working-path.simulations.folder"
					)
				).findFirst().orElseGet(() -> null);
			LogUtils.info("\n\n");
			String simulationSummaryFile = simulationSummaryFolder + File.separator + "Summary.xlsx";
			SimpleWorkbookTemplate workBookTemplate = new SimpleWorkbookTemplate(true);
			Sheet summarySheet = workBookTemplate.getOrCreateSheet("Riepilogo", true);
			List<String> headerLabels = new ArrayList<>();
			List<String> headerLabelsTemp = new ArrayList<>(SELotterySimpleSimulator.excelHeaderLabels);
			List<String> headersToBeSkipped = Arrays.asList(
				SELotterySimpleSimulator.DATA_AGGIORNAMENTO_STORICO_LABEL,
				SELotterySimpleSimulator.FILE_LABEL
			);
			headerLabelsTemp.removeAll(headersToBeSkipped);
			headerLabels.add(SELotterySimpleSimulator.FILE_LABEL);
			headerLabels.addAll(headerLabelsTemp);
			headerLabels.set(headerLabels.indexOf(SELotterySimpleSimulator.DATA_LABEL), "Conteggio estrazioni");
			workBookTemplate.createHeader(true, headerLabels);
			AtomicInteger reportCounter = new AtomicInteger(0);
			process(simulationSummaryFolder, workBookTemplate, headersToBeSkipped, reportCounter);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.FILE_LABEL), 15000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.COSTO_STORICO_LABEL), 3000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.RITORNO_STORICO_LABEL), 3000);
			try (OutputStream destFileOutputStream = new FileOutputStream(simulationSummaryFile)){
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
		String folderAbsolutePath,
		SimpleWorkbookTemplate workBookTemplate,
		List<String> headersToBeSkipped,
		AtomicInteger reportCounter
	) {
		for (File singleSimFolder : new File(folderAbsolutePath).listFiles(
			(file, name) -> {
				File currentIteratedFile = new File(file, name);
				return currentIteratedFile.isDirectory() && !currentIteratedFile.getName().equals(SELotteryComplexSimulator.GENERATED_FOLDER_NAME);
			})
		) {
			LogUtils.info("Scanning " + singleSimFolder.getAbsolutePath());
			File report = Arrays.stream(singleSimFolder.listFiles((file, name) -> name.endsWith("report.xlsx"))).findFirst().orElseGet(() -> null);
			if (report != null) {
				reportCounter.incrementAndGet();
				try {
					process(workBookTemplate, headersToBeSkipped, singleSimFolder, report);
				} catch (Throwable exc) {
					LogUtils.warn("Unable to process " + report.getAbsolutePath() + ": " + exc.getMessage());
					List<File> backupFiles = ResourceUtils.INSTANCE.findReverseOrdered("report - ", "xlsx", report.getParentFile().getAbsolutePath());
					boolean processed = false;
					if (backupFiles.size() > 0) {
						LogUtils.info("Trying to process its backups");
						for (File backup : backupFiles) {
							try {
								process(workBookTemplate, headersToBeSkipped, singleSimFolder, backup);
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
			process(singleSimFolder.getAbsolutePath(), workBookTemplate, headersToBeSkipped,reportCounter);
		}
	}

	protected static void process(
		SimpleWorkbookTemplate summaryWorkBookTemplate,
		List<String> headersToBeSkipped,
		File singleSimFolder,
		File report
	) throws IOException {
		try (InputStream inputStream = new FileInputStream(report.getAbsolutePath());Workbook simulationWorkBook = new XSSFWorkbook(inputStream);) {
			FormulaEvaluator evaluator = simulationWorkBook.getCreationHelper().createFormulaEvaluator();
			Sheet resultSheet = simulationWorkBook.getSheet("Risultati");
			summaryWorkBookTemplate.addRow();
			Cell cellName = summaryWorkBookTemplate.addCell(report.getName()).get(0);
			summaryWorkBookTemplate.setLinkForCell(
				HyperlinkType.FILE,
				cellName,
				URLEncoder.encode(singleSimFolder.getName() + "/" + report.getName(), "UTF-8").replace("+", "%20")
			);
			String historicalTombolaLabel = SELotterySimpleSimulator.getHistoryPremiumLabel(Premium.LABEL_TOMBOLA);
			for (String cellLabel : SELotterySimpleSimulator.excelHeaderLabels) {
				if (!headersToBeSkipped.contains(cellLabel)) {
					Cell simulationCell = resultSheet.getRow(1).getCell(Shared.getCellIndex(resultSheet, cellLabel));
					CellValue simulationCellValue = evaluator.evaluate(simulationCell);
					if (simulationCellValue.getCellType().equals(CellType.NUMERIC)) {
						Cell summaryCurrentCell = summaryWorkBookTemplate.addCell(simulationCellValue.getNumberValue(), "#,##0");
						if (cellLabel.equals(Premium.LABEL_CINQUINA) && simulationCellValue.getNumberValue() > 0) {
							Shared.toHighlightedBoldedCell(summaryWorkBookTemplate.getWorkbook(), summaryCurrentCell, IndexedColors.YELLOW);
						} else if (cellLabel.equals(Premium.LABEL_TOMBOLA) && simulationCellValue.getNumberValue() > 0) {
							Shared.toHighlightedBoldedCell(summaryWorkBookTemplate.getWorkbook(), summaryCurrentCell, IndexedColors.RED);
						} else if (cellLabel.equals(historicalTombolaLabel) && simulationCellValue.getNumberValue() > 0) {
							Iterator<Row> rowIterator = resultSheet.rowIterator();
							rowIterator.next();
							rowIterator.next();
							while (rowIterator.hasNext()) {
								Cell historicalTombolaCell = rowIterator.next().getCell(Shared.getCellIndex(resultSheet, historicalTombolaLabel));
								if (historicalTombolaCell.getCellStyle().getFillForegroundColor() == IndexedColors.RED.getIndex()) {
									Shared.toHighlightedBoldedCell(summaryWorkBookTemplate.getWorkbook(), summaryCurrentCell, IndexedColors.RED);
									break;
								}
							}
						}
					} else if (simulationCellValue.getCellType().equals(CellType.STRING)) {
						summaryWorkBookTemplate.addCell(simulationCellValue.getStringValue());
					}
				}
			}
		}
	}

}
