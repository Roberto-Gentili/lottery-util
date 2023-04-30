package org.rg.game.lottery.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;
import org.rg.game.lottery.engine.TimeUtils;

public class SEMassiveVerifierAndQualityChecker {

	public static void main(String[] args) throws IOException {
		check(
			forDate(
				Shared.getSystemEnv(
					"startDate", "14/02/2023"
				), Shared.getSystemEnv(
					"endDate", "next+1"
				),
				false
			)
		);
	}

	static List<Map.Entry<LocalDate, Object>> forDate(
		String startDateAsString,
		String endDateAsString,
		Boolean printReportDetail
	) {
		LotteryMatrixGeneratorAbstEngine engine = new SELotteryMatrixGeneratorEngine();
		LocalDate startDate = Shared.convert(startDateAsString);
		LocalDate endDate =  Shared.convert(endDateAsString);
		List<Map.Entry<LocalDate, Object>> dates = new ArrayList<>();
		while (startDate.compareTo(endDate) <= 0) {
			startDate = engine.computeNextExtractionDate(startDate, false);
			dates.add(new AbstractMap.SimpleEntry<LocalDate, Object>(startDate, printReportDetail));
			startDate =  engine.computeNextExtractionDate(startDate.plus(1, ChronoUnit.DAYS), false);
		}
		//dates.add(new AbstractMap.SimpleEntry(LocalDate.parse("2023-04-24"), false));
		return dates;
	}

	private static void check(List<Map.Entry<LocalDate, Object>>... dateGroupsList) throws IOException {
		Map<String, Map<Integer,List<List<Integer>>>> historyData = new LinkedHashMap<>();
		Map<Integer,List<List<Integer>>> globalData = new LinkedHashMap<>();
		Map<Integer,Map<String, Map<Integer, Integer>>> dataForTime = new LinkedHashMap<>();
		LocalDateTime backupTime = LocalDateTime.now();
		for (List<Map.Entry<LocalDate, Object>> dateGroup: dateGroupsList) {
			for (Map.Entry<LocalDate, Object> dateInfo : dateGroup) {
				String extractionDate = TimeUtils.defaultLocalDateFormatter.format(dateInfo.getKey());
				String extractionYear = extractionDate.split("\\/")[2];
				String extractionMonth = Shared.getMonth(extractionDate);
				String extractionDay = extractionDate.split("\\/")[0];
				File mainFile = Shared.getSystemsFile(extractionYear);
				Shared.backup(backupTime, mainFile);
				List<List<Integer>> system = new ArrayList<>();
				try (InputStream srcFileInputStream = new FileInputStream(mainFile);
					Workbook workbook = new XSSFWorkbook(srcFileInputStream);
				) {
					XSSFFont boldFont = (XSSFFont) workbook.createFont();
					boldFont.setBold(true);
					CellStyle boldAndCeneteredCellStyle = workbook.createCellStyle();
					boldAndCeneteredCellStyle.setFont(boldFont);
					boldAndCeneteredCellStyle.setAlignment(HorizontalAlignment.CENTER);
					boldAndCeneteredCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					boldAndCeneteredCellStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
					CellStyle normalAndCeneteredCellStyle = workbook.createCellStyle();
					normalAndCeneteredCellStyle.setAlignment(HorizontalAlignment.CENTER);
					Sheet sheet = workbook.getSheet(extractionMonth);
					if (sheet == null) {
						System.out.println("Nessun foglio da verificare per il mese " + extractionMonth);
						continue;
					}
					int offset = Shared.getCellIndex(sheet, extractionDay);
					if (offset < 0) {
						System.out.println("Nessuna combinazione da verificare per la data " + extractionDate + "\n");
						continue;
					}
					Iterator<Row> rowIterator = sheet.rowIterator();
					rowIterator.next();
					List<Integer> winningCombo = Shared.getSEStats().getWinningComboOf(dateInfo.getKey());
					while (rowIterator.hasNext()) {
						Row row = rowIterator.next();
						List<Integer> currentCombo = new ArrayList<>();
						for (int i = 0; i < 6; i++) {
							Cell cell = row.getCell(offset + i);
							try {
								Integer currentNumber = Integer.valueOf((int)cell.getNumericCellValue());
								currentCombo.add(currentNumber);
								if (winningCombo != null && winningCombo.contains(currentNumber)) {
									cell.setCellStyle(boldAndCeneteredCellStyle);
								} else {
									cell.setCellStyle(normalAndCeneteredCellStyle);
								}
							} catch (NullPointerException exc) {
								if (cell == null) {
									break;
								}
								throw exc;
							} catch (IllegalStateException exc) {
								if (cell.getStringCellValue().equals("Risultato estrazione")) {
									break;
								}
								throw exc;
							}
						}
						if (currentCombo.isEmpty() || currentCombo.get(0) == 0) {
							break;
						}
						system.add(currentCombo);
					}
					rowIterator = sheet.rowIterator();
					sheet.setColumnWidth(offset + 6, 6600);
					rowIterator.next();
					Cell cell = rowIterator.next().getCell(offset + 6);
					XSSFRichTextString results = new XSSFRichTextString();
					System.out.println(
						checkCombo(
							globalData,
							dataForTime,
							dateInfo.getKey(),
							system,
							winningCombo,
							results,
							boldFont
						)
					);
					if (results.getString() != null) {
						results.append("\n");
					}
					checkInHistory(
						historyData,
						dateInfo.getKey(),
						system,
						Shared.getSEStats().getAllWinningCombos(),
						results,
						boldFont
					);
					if (results.getString() != null) {
						cell.setCellValue(results);
					}
					try (OutputStream destFileOutputStream = new FileOutputStream(mainFile.getAbsolutePath())){
						workbook.write(destFileOutputStream);
					}
				} catch (Throwable exc) {
					exc.printStackTrace();
				}
			}
		}
		writeAndPrintData(globalData, dataForTime);
	}

	private static void writeAndPrintData(
		Map<Integer, List<List<Integer>>> globalData,
		Map<Integer, Map<String, Map<Integer, Integer>>> dataForTime
	) throws IOException {
		System.out.println("\nRisultati per tempo:");
		for (Map.Entry<Integer, Map<String, Map<Integer, Integer>>> yearAndDataForMonth : dataForTime.entrySet()) {
			int year = yearAndDataForMonth.getKey();
			Map<String, Map<Integer, Integer>> dataForMonth = yearAndDataForMonth.getValue();
			System.out.println("\t" + year + ":");
			File mainFile = Shared.getSystemsFile(year);
			try (InputStream srcFileInputStream = new FileInputStream(mainFile);
				Workbook workbook = new XSSFWorkbook(srcFileInputStream);
			) {
				Sheet sheet = Shared.getSummarySheet(workbook);
				Iterator<Row> rowIterator = sheet.rowIterator();
				Font normalFont = null;
				Font boldFont = null;
				CellStyle valueStyle = null;
				int rowIndex = 0;
				Row header;
				if (rowIterator.hasNext()) {
					header = rowIterator.next();
				} else {
					boldFont = workbook.createFont();
					boldFont.setBold(true);
					normalFont = workbook.createFont();
					normalFont.setBold(false);
					sheet.createFreezePane(0, 1);
					header =  sheet.createRow(rowIndex);
					CellStyle headerStyle = workbook.createCellStyle();
					headerStyle.setFont(boldFont);
					headerStyle.setAlignment(HorizontalAlignment.CENTER);
					headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					headerStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
					Cell monthLabel = header.createCell(0);
					monthLabel.setCellValue("Mese");
					monthLabel.setCellStyle(headerStyle);
					int columnIndex = 1;
					for (String premiumLabel :  SEStats.allPremiumLabels()) {
						Cell headerCell = header.createCell(columnIndex++);
						headerCell.setCellStyle(headerStyle);
						headerCell.setCellValue(premiumLabel);
					}
					valueStyle = workbook.createCellStyle();
					valueStyle.setFont(normalFont);
					valueStyle.setAlignment(HorizontalAlignment.RIGHT);
					valueStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
				}
				rowIndex++;
				for (Map.Entry<String, Map<Integer, Integer>> monthWinningInfo : dataForMonth.entrySet()) {
					String month = monthWinningInfo.getKey();
					System.out.println("\t\t" + month + ":");
					Map<Integer, Integer> winningInfo = monthWinningInfo.getValue();
					Row row = rowIterator.hasNext() ? rowIterator.next() : sheet.createRow(rowIndex);
					if (row.getCell(0) == null) {
						Cell labelCell = row.createCell(0);
						labelCell.getCellStyle().setFont(boldFont);
						labelCell.getCellStyle().setAlignment(HorizontalAlignment.LEFT);
						labelCell.setCellValue(month);
					}
					for (Map.Entry<Integer, Integer> typeAndCounter : winningInfo.entrySet()) {
						Integer type = typeAndCounter.getKey();
						Integer counter = typeAndCounter.getValue();
						String label = SEStats.toPremiumLabel(type);
						int labelIndex = Shared.getCellIndex(sheet, label);
						System.out.println("\t\t\t" + label + ":" + SEStats.rightAlignedString(Shared.integerFormat.format(counter), 21 - label.length()));
						Cell valueCell = row.getCell(labelIndex);
						if (valueCell == null) {
							valueCell = row.createCell(labelIndex);
							valueCell.setCellStyle(valueStyle);
						}
						valueCell.setCellValue(counter);
						if (rowIndex == dataForMonth.entrySet().size()) {
							Row summaryRow = sheet.getRow(rowIndex + 1) != null ?
								sheet.getRow(rowIndex + 1) : sheet.createRow(rowIndex + 1);
							if (summaryRow.getCell(0) == null) {
								Cell labelCell = summaryRow.createCell(0);
								labelCell.getCellStyle().setFont(boldFont);
								labelCell.getCellStyle().setAlignment(HorizontalAlignment.LEFT);
								labelCell.setCellValue("Totale");
							}
							valueCell = summaryRow.getCell(labelIndex);
							if (valueCell == null) {
								valueCell = summaryRow.createCell(labelIndex);
								valueCell.setCellStyle(valueStyle);
							}
							String columnName = CellReference.convertNumToColString(labelIndex);
							valueCell.setCellFormula("SUM(" + columnName + "2:"+ columnName + (rowIndex + 1) +")");
						}
					}
					rowIndex++;
				}
				XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
				try (OutputStream destFileOutputStream = new FileOutputStream(mainFile.getAbsolutePath())){
					workbook.write(destFileOutputStream);
				}
			} catch (Throwable exc) {
				System.err.println("Unable to process file: " + exc.getMessage());
			}
		};

		System.out.println("\nRisultati globali:");
		globalData.forEach((key, combos) -> {
			String label = SEStats.toPremiumLabel(key);
			System.out.println("\t" + label + ":" + SEStats.rightAlignedString(Shared.integerFormat.format(combos.size()), 21 - label.length()));
		});
	}

	private static String checkCombo(
		Map<Integer,List<List<Integer>>> globalData,
		Map<Integer, Map<String, Map<Integer, Integer>>> dataForTime,
		LocalDate extractionDate,
		List<List<Integer>> combosToBeChecked,
		List<Integer> winningCombo,
		XSSFRichTextString results,
		XSSFFont boldFont
	) {
		if (winningCombo == null || winningCombo.isEmpty()) {
			return "Nessuna estrazione per il concorso del " + TimeUtils.defaultLocalDateFormatter.format(extractionDate) + "\n";
		}
		Map<Integer,List<List<Integer>>> winningCombos = new TreeMap<>();
		Collection<Integer> hitNumbers = new LinkedHashSet<>();
		for (List<Integer> currentCombo : combosToBeChecked) {
			Integer hit = 0;
			for (Integer currentNumber : currentCombo) {
				if (winningCombo.contains(currentNumber)) {
					hitNumbers.add(currentNumber);
					hit++;
				}
			}
			if (hit > 1) {
				winningCombos.computeIfAbsent(hit, ht -> new ArrayList<>()).add(currentCombo);
				Map<Integer, Integer> winningCounter = dataForTime.computeIfAbsent(extractionDate.getYear(), year -> new LinkedHashMap<>()).computeIfAbsent(
					Shared.getMonth(extractionDate), monthLabel -> new LinkedHashMap<>()
				);
				winningCounter.put(hit, winningCounter.computeIfAbsent(hit, key -> Integer.valueOf(0)) + 1);
				globalData.computeIfAbsent(hit, ht -> new ArrayList<>()).add(currentCombo);
			}
		}
		results.append("Vincente", boldFont);
		results.append(": ");
		results.append(
			Shared.toString(winningCombo, ", ") + "\n\n"
		);
		results.append("Concorso", boldFont);
		results.append(": ");
		if (!winningCombos.isEmpty()) {
			results.append("\n");
			printSummaryWinningInfo(winningCombo, results, boldFont, winningCombos);
			//printDetailedWinningInfo(winningCombo, results, boldFont, winningCombos);
		} else {
			results.append("nessuna vincita");
		}
		results.append("\n");
		StringBuffer result = new StringBuffer();
		if (!winningCombo.isEmpty()) {
			if (!winningCombos.isEmpty()) {
				result.append("Numeri estratti per il *superenalotto* del " + TimeUtils.defaultLocalDateFormatter.format(extractionDate) +": " + Shared.toWAString(winningCombo, ", ", hitNumbers) + "\n");
				for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
					result.append("\t*Combinazioni con " + SEStats.toPremiumLabel(combos.getKey()).toLowerCase() + "*:" + "\n");
					for (List<Integer> combo : combos.getValue()) {
						result.append("\t\t" +
							Shared.toWAString(combo, "\t", winningCombo) + "\n"
						);
					}
				}
			} else {
				result.append("Nessuna vincita per il concorso del " + TimeUtils.defaultLocalDateFormatter.format(extractionDate) + "\n");
			}
		}
		return result.toString();
	}

	private static void printDetailedWinningInfo(
		List<Integer> winningCombo,
		XSSFRichTextString results,
		XSSFFont boldFont,
		Map<Integer, List<List<Integer>>> winningCombos
	) {
		for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
			results.append("  " + SEStats.toPremiumLabel(combos.getKey()), boldFont);
			results.append(":" + "\n");
			Iterator<List<Integer>> combosIterator = combos.getValue().iterator();
			while (combosIterator.hasNext()) {
				List<Integer> currentCombo = combosIterator.next();
				results.append("    ");
				Iterator<Integer> winningComboIterator = currentCombo.iterator();
				while (winningComboIterator.hasNext()) {
					Integer number = winningComboIterator.next();
					if (winningCombo.contains(number)) {
						results.append(number.toString(), boldFont);
					} else {
						results.append(number.toString());
					}
					if (winningComboIterator.hasNext()) {
						results.append(", ");
					}
				}
				if (combosIterator.hasNext()) {
					results.append("\n");
				}
			}
		}
	}

	private static void printSummaryWinningInfo(
		List<Integer> winningCombo,
		XSSFRichTextString results,
		XSSFFont boldFont,
		Map<Integer, List<List<Integer>>> winningCombos
	) {
		Iterator<Map.Entry<Integer, List<List<Integer>>>> winningAndCombosIterator = winningCombos.entrySet().iterator();
		while (winningAndCombosIterator.hasNext()) {
			Map.Entry<Integer, List<List<Integer>>> combos = winningAndCombosIterator.next();
			results.append("  " + SEStats.toPremiumLabel(combos.getKey()), boldFont);
			results.append(": " + combos.getValue().size());
			if (winningAndCombosIterator.hasNext()) {
				results.append("\n");
			}
		}
	}

	private static void checkInHistory(
		Map<String, Map<Integer,List<List<Integer>>>> historyData,
		LocalDate extractionDate,
		List<List<Integer>> system,
		Map<Date, List<Integer>> allWinningCombos,
		XSSFRichTextString results,
		XSSFFont boldFont
	) {
		Collection<Integer> hitNumbers = new LinkedHashSet<>();
		String extractionDateAsString = TimeUtils.defaultLocalDateFormatter.format(extractionDate);
		for (List<Integer> winningCombo : allWinningCombos.values()) {
			for (List<Integer> currentCombo : system) {
				Integer hit = 0;
				for (Integer currentNumber : currentCombo) {
					if (winningCombo.contains(currentNumber)) {
						hitNumbers.add(currentNumber);
						hit++;
					}
				}
				if (hit > 1) {
					historyData.computeIfAbsent(extractionDateAsString, key -> new TreeMap<>())
					.computeIfAbsent(hit, ht -> new ArrayList<>()).add(currentCombo);
				}
			}
		}
		results.append("Storico", boldFont);
		results.append(": ");
		Map<Integer,List<List<Integer>>> systemResultsInHistory = historyData.get(extractionDateAsString);
		if (systemResultsInHistory != null) {
			results.append("\n");
			for (Map.Entry<Integer, List<List<Integer>>> singleHistoryResult : systemResultsInHistory.entrySet()) {
				String label = SEStats.toPremiumLabel(singleHistoryResult.getKey());
				results.append("  ");
				results.append(label, boldFont);
				results.append(": " + Shared.integerFormat.format(singleHistoryResult.getValue().size()) + "\n");
			}
		} else {
			results.append("nessuna vincita");
		}
	}

}
