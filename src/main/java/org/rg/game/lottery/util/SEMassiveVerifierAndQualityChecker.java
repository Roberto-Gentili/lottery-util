package org.rg.game.lottery.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;

public class SEMassiveVerifierAndQualityChecker {

	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Shared.standardDatePattern.toPattern());

	public static void main(String[] args) throws IOException {
		check(
			forDate("14/02/2023", "today", false)
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
		return dates;
	}

	private static void check(List<Map.Entry<LocalDate, Object>>... dateGroupsList) throws IOException {
		Map<String, Map<Integer,List<List<Integer>>>> historyData = new LinkedHashMap<>();
		Map<Integer,List<List<Integer>>> globalData = new LinkedHashMap<>();
		Map<Integer,Map<String, Map<Integer, Integer>>> dataForTime = new LinkedHashMap<>();
		for (List<Map.Entry<LocalDate, Object>> dateGroup: dateGroupsList) {
			for (Map.Entry<LocalDate, Object> dateInfo : dateGroup) {
				String extractionDate = formatter.format(dateInfo.getKey());
				String extractionYear = extractionDate.split("\\/")[2];
				String extractionMonth = Shared.getMonth(extractionDate);
				String extractionDay = extractionDate.split("\\/")[0];
				FileSystemItem mainFile = Shared.getSystemsFile(extractionYear);
				mainFile.reset();
				List<List<Integer>> system = new ArrayList<>();
				try (InputStream srcFileInputStream = mainFile.toInputStream();
					OutputStream destFileOutputStream =	new FileOutputStream(mainFile.getAbsolutePath());
					Workbook workbook = new XSSFWorkbook(srcFileInputStream);
				) {
					XSSFFont boldFont = (XSSFFont) workbook.createFont();
					boldFont.setBold(true);
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
					while (rowIterator.hasNext()) {
						Row row = rowIterator.next();
						List<Integer> currentCombo = new ArrayList<>();
						for (int i = 0; i < 6; i++) {
							Cell cell = row.getCell(offset + i);
							try {
								Integer currentNumber = Integer.valueOf((int)cell.getNumericCellValue());
								currentCombo.add(currentNumber);
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
					sheet.setColumnWidth(offset + 6, 5800);
					rowIterator.next();
					Cell cell = rowIterator.next().getCell(offset + 6);
					XSSFRichTextString results = new XSSFRichTextString();
					System.out.println(
						checkCombo(
							globalData,
							dataForTime,
							dateInfo.getKey(),
							system,
							SEStats.get(Shared.SEStatsDefaultDate).getWinningComboOf(dateInfo.getKey()),
							results,
							boldFont
						)
					);
					results.append("\n");
					checkInHistory(
						historyData,
						dateInfo.getKey(),
						system,
						SEStats.get(Shared.SEStatsDefaultDate).getAllWinningCombos(),
						results,
						boldFont
					);
					cell.setCellValue(results);
					workbook.write(destFileOutputStream);
				} catch (Throwable exc) {
					exc.printStackTrace();
				}
			}
		}
		writeAndPrintData(globalData, dataForTime);
	}

	private static void writeAndPrintData(Map<Integer, List<List<Integer>>> globalData,
			Map<Integer, Map<String, Map<Integer, Integer>>> dataForTime) {
		System.out.println("\nRisultati per tempo:");
		for (Map.Entry<Integer, Map<String, Map<Integer, Integer>>> yearAndDataForMonth : dataForTime.entrySet()) {
			int year = yearAndDataForMonth.getKey();
			Map<String, Map<Integer, Integer>> dataForMonth = yearAndDataForMonth.getValue();
			System.out.println("\t" + year + ":");
			FileSystemItem mainFile = Shared.getSystemsFile(year);
			mainFile.reset();
			AtomicInteger summaryRowIndex = new AtomicInteger(0);
			try (InputStream srcFileInputStream = mainFile.toInputStream();
				OutputStream destFileOutputStream =	new FileOutputStream(mainFile.getAbsolutePath());
				Workbook workbook = new XSSFWorkbook(srcFileInputStream);
			) {
				Sheet sheet = Shared.getSummarySheet(workbook);
				Iterator<Row> rowIterator = sheet.rowIterator();
				Font normalFont = null;
				Font boldFont = null;
				CellStyle valueStyle = null;
				Row header;
				if (rowIterator.hasNext()) {
					header = rowIterator.next();
				} else {
					boldFont = workbook.createFont();
					boldFont.setBold(true);
					normalFont = workbook.createFont();
					normalFont.setBold(false);
					sheet.createFreezePane(0, 1);
					header =  sheet.createRow(summaryRowIndex.getAndIncrement());
					CellStyle headerStyle = workbook.createCellStyle();
					headerStyle.setFont(boldFont);
					headerStyle.setAlignment(HorizontalAlignment.CENTER);
					headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					headerStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
					Cell monthLabel = header.createCell(0);
					monthLabel.setCellValue("Mese");
					monthLabel.setCellStyle(headerStyle);
					int columnIndex = 1;
					for (String premiumLabel : Shared.allPremiumLabels()) {
						Cell headerCell = header.createCell(columnIndex++);
						headerCell.setCellStyle(headerStyle);
						headerCell.setCellValue(premiumLabel);
					}
					valueStyle = workbook.createCellStyle();
					valueStyle.setFont(normalFont);
					valueStyle.setAlignment(HorizontalAlignment.RIGHT);
				}
				for (Map.Entry<String, Map<Integer, Integer>> monthWinningInfo : dataForMonth.entrySet()) {
					String month = monthWinningInfo.getKey();
					System.out.println("\t\t" + month + ":");
					Map<Integer, Integer> winningInfo = monthWinningInfo.getValue();
					Row row = rowIterator.hasNext() ? rowIterator.next() : sheet.createRow(summaryRowIndex.getAndIncrement());
					if (row.getCell(0) == null) {
						Cell labelCell = row.createCell(0);
						labelCell.getCellStyle().setFont(boldFont);
						labelCell.getCellStyle().setAlignment(HorizontalAlignment.LEFT);
						labelCell.setCellValue(month);
					}
					for (Map.Entry<Integer, Integer> typeAndCounter : winningInfo.entrySet()) {
						Integer type = typeAndCounter.getKey();
						Integer counter = typeAndCounter.getValue();
						String label = SEStats.toLabel(type);
						System.out.println("\t\t\t" + label + ":" + SEStats.rightAlignedString(Shared.integerFormat.format(counter), 21 - label.length()));
						Cell valueCell = row.getCell(Shared.getCellIndex(sheet, label));
						if (valueCell == null) {
							valueCell = row.createCell(Shared.getCellIndex(sheet, label));
							valueCell.setCellStyle(valueStyle);
						}
						valueCell.setCellValue(counter);
					};

				};
				workbook.write(destFileOutputStream);
			} catch (Throwable exc) {
				System.err.println("Unable to process file: " + exc.getMessage());
			}
		};

		System.out.println("\nRisultati globali:");
		globalData.forEach((key, combos) -> {
			String label = SEStats.toLabel(key);
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
		results.append("Concorso", boldFont);
		results.append(": ");
		if (!winningCombo.isEmpty()) {
			if (!winningCombos.isEmpty()) {
				results.append("\n");
				for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
					results.append("  " + Shared.toPremiumLabel(combos.getKey()), boldFont);
					results.append(":" + "\n");
					for (List<Integer> combo : combos.getValue()) {
						results.append("    " +
							Shared.toString(combo, ", ", winningCombo) + "\n"
						);
					}
				}
			} else {
				results.append("nessuna vincita\n");
			}
		}
		StringBuffer result = new StringBuffer();
		if (!winningCombo.isEmpty()) {
			if (!winningCombos.isEmpty()) {
				result.append("Numeri estratti per il *superenalotto* del " + Shared.formatter.format(extractionDate) +": " + Shared.toWAString(winningCombo, ", ", hitNumbers) + "\n");
				for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
					result.append("\t*Combinazioni con " + Shared.toPremiumLabel(combos.getKey()).toLowerCase() + "*:" + "\n");
					for (List<Integer> combo : combos.getValue()) {
						result.append("\t\t" +
							Shared.toWAString(combo, "\t", winningCombo) + "\n"
						);
					}
				}
			} else {
				result.append("Nessuna vincita per il concorso del " + Shared.formatter.format(extractionDate) + "\n");
			}
		}
		return result.toString();
	}

	private static void checkInHistory(
		Map<String, Map<Integer,List<List<Integer>>>> historyData,
		LocalDate extractionDate,
		List<List<Integer>> system,
		Map<Date, List<Integer>> allWinningCombos, XSSFRichTextString results, XSSFFont boldFont
	) {
		Collection<Integer> hitNumbers = new LinkedHashSet<>();
		String extractionDateAsString = formatter.format(extractionDate);
		for (List<Integer> winnginCombo : allWinningCombos.values()) {
			for (List<Integer> currentCombo : system) {
				Integer hit = 0;
				for (Integer currentNumber : currentCombo) {
					if (winnginCombo.contains(currentNumber)) {
						hitNumbers.add(currentNumber);
						hit++;
					}
				}
				if (hit > 1) {
					historyData.computeIfAbsent(extractionDateAsString, key -> new LinkedHashMap<>())
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
				String label = SEStats.toLabel(singleHistoryResult.getKey());
				results.append("  ");
				results.append(label, boldFont);
				results.append(": " + Shared.integerFormat.format(singleHistoryResult.getValue().size()) + "\n");
			}
		} else {
			results.append("nessuna vincita");
		}
	}

}
