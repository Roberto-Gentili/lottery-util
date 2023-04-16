package org.rg.game.lottery.util;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;

public class SEMassiveVerifier {

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
		while (startDate.compareTo(endDate) < 0) {
			startDate = engine.computeNextExtractionDate(startDate, false);
			dates.add(new AbstractMap.SimpleEntry<LocalDate, Object>(startDate, printReportDetail));
			startDate =  engine.computeNextExtractionDate(startDate.plus(1, ChronoUnit.DAYS), false);
		}
		return dates;
	}

	private static void check(List<Map.Entry<LocalDate, Object>>... dateGroupsList) throws IOException {
		Map<Integer,List<List<Integer>>> globalData = new LinkedHashMap<>();
		Map<Integer,Map<String, Map<Integer, Integer>>> dataForTime = new LinkedHashMap<>();
		for (List<Map.Entry<LocalDate, Object>> dateGroup: dateGroupsList) {
			for (Map.Entry<LocalDate, Object> dateInfo : dateGroup) {
				String extractionDate = Shared.formatter.format(dateInfo.getKey());
				String extractionYear = extractionDate.split("\\/")[2];
				String extractionMonth = Shared.getMonth(extractionDate);
				String extractionDay = extractionDate.split("\\/")[0];
				FileSystemItem mainFile = Shared.getSystemsFile(extractionYear);
				List<List<Integer>> system = new ArrayList<>();
				try (InputStream srcFileInputStream = mainFile.toInputStream(); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
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
							}
						}
						if (currentCombo.isEmpty() || currentCombo.get(0) == 0) {
							break;
						}
						system.add(currentCombo);
					}
					System.out.println(
						checkCombo(
							globalData,
							dataForTime,
							dateInfo.getKey(),
							system,
							SEStats.get(Shared.SEStatsDefaultDate).getWinningComboOf(dateInfo.getKey())
						)
					);
				} catch (Throwable exc) {
					exc.printStackTrace();
				}
			}
		}
		System.out.println("\nRisultati per tempo:");
		dataForTime.forEach((year, dataForMonth) -> {
			System.out.println("\t" + year + ":");
			dataForMonth.forEach((month, winningInfo) -> {
				System.out.println("\t\t" + month + ":");
				winningInfo.forEach((type, counter) -> {
					String label = SEStats.toLabel(type);
					System.out.println("\t\t\t" + label + ":" + SEStats.rightAlignedString(Shared.integerFormat.format(counter), 21 - label.length()));
				});
			});
		});
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
		List<Integer> winningCombo
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
		StringBuffer result = new StringBuffer();
		if (!winningCombo.isEmpty()) {
			if (!winningCombos.isEmpty()) {
				result.append("Numeri estratti per il *superenalotto* del " + Shared.formatter.format(extractionDate) +": " + toString(winningCombo, ", ", hitNumbers) + "\n");
				for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
					result.append("\t*Combinazioni con " + Shared.toLabel(combos.getKey()).toLowerCase() + "*:" + "\n");
					for (List<Integer> combo : combos.getValue()) {
						result.append("\t\t" +
							toString(combo, "\t", winningCombo) + "\n"
						);
					}
				}
			} else {
				result.append("Nessuna vincita per il concorso del " + Shared.formatter.format(extractionDate) + "\n");
			}
		}
		return result.toString();
	}

	private static String toString(Collection<Integer> combo, String separator, Collection<Integer> numbers) {
		return String.join(
			separator,
			combo.stream()
		    .map(val -> {
		    	boolean hit = numbers.contains(val);
		    	return (hit ? "*" : "") + val.toString() + (hit ? "*" : "");
		    })
		    .collect(Collectors.toList())
		);
	}

}
