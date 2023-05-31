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
import java.util.List;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.formula.BaseFormulaEvaluator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.LogUtils;
import org.rg.game.core.ResourceUtils;
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
			int reportCounter = 0;
			for (File singleSimFolder : new File(simulationSummaryFolder).listFiles((file, name) -> new File(file, name).isDirectory())) {
				File report = Arrays.stream(singleSimFolder.listFiles((file, name) -> name.endsWith("report.xlsx"))).findFirst().orElseGet(() -> null);
				if (report != null) {
					reportCounter++;
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
			}
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.FILE_LABEL), 15000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.COSTO_STORICO_LABEL), 3000);
			summarySheet.setColumnWidth(Shared.getCellIndex(summarySheet, SELotterySimpleSimulator.RITORNO_STORICO_LABEL), 3000);
			try (OutputStream destFileOutputStream = new FileOutputStream(simulationSummaryFile)){
				workBookTemplate.setAutoFilter(0, reportCounter, 0, headerLabels.size() - 1);
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
		SimpleWorkbookTemplate workBookTemplate,
		List<String> headersToBeSkipped,
		File singleSimFolder,
		File report
	) throws IOException {
		try (InputStream inputStream = new FileInputStream(report.getAbsolutePath());Workbook workBook = new XSSFWorkbook(inputStream);) {
			FormulaEvaluator evaluator = workBook.getCreationHelper().createFormulaEvaluator();
			Sheet resultSheet = workBook.getSheet("Risultati");
			workBookTemplate.addRow();
			Cell cellName = workBookTemplate.addCell(report.getName()).get(0);
			workBookTemplate.setLinkForCell(
				HyperlinkType.FILE,
				cellName,
				URLEncoder.encode(singleSimFolder.getName() + "/" + report.getName(), "UTF-8").replace("+", "%20")
			);
			for (String cellLabel : SELotterySimpleSimulator.excelHeaderLabels) {
				if (!headersToBeSkipped.contains(cellLabel)) {
					Cell cell = resultSheet.getRow(1).getCell(Shared.getCellIndex(resultSheet, cellLabel));
					CellValue cellValue = evaluator.evaluate(cell);
					if (cellValue.getCellType().equals(CellType.NUMERIC)) {
						workBookTemplate.addCell(cellValue.getNumberValue(), "#,##0");
					} else if (cellValue.getCellType().equals(CellType.STRING)) {
						workBookTemplate.addCell(cellValue.getStringValue());
					}
				}
			}
		}
	}

}
