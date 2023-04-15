package org.rg.game.lottery.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;

public class SEMassiveQualityChecker {

	protected static DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	static SimpleDateFormat standardDatePattern = new SimpleDateFormat("dd/MM/yyyy");
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(standardDatePattern.toPattern());
	private static String SEStatsDefaultDate = "02/07/2009";

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
		LocalDate startDate = convert(startDateAsString);
		LocalDate endDate =  convert(endDateAsString);
		List<Map.Entry<LocalDate, Object>> dates = new ArrayList<>();
		while (startDate.compareTo(endDate) <= 0) {
			startDate = engine.computeNextExtractionDate(startDate, false);
			dates.add(new AbstractMap.SimpleEntry<LocalDate, Object>(startDate, printReportDetail));
			startDate =  engine.computeNextExtractionDate(startDate.plus(1, ChronoUnit.DAYS), false);
		}
		return dates;
	}

	static LocalDate convert(String dateAsString) {
		if (dateAsString.equals("today")) {
			return LocalDateTime.now(ZoneId.of("Europe/Rome")).toLocalDate();
		}
		return LocalDate.parse(dateAsString, formatter);
	}

	private static void check(List<Map.Entry<LocalDate, Object>>... dateGroupsList) throws IOException {
		Map<String, Map<Integer,List<List<Integer>>>> historyData = new LinkedHashMap<>();
		Map<Integer,List<List<Integer>>> globalData = new LinkedHashMap<>();
		Map<Integer,Map<String, Map<Integer, Integer>>> dataForTime = new LinkedHashMap<>();
		for (List<Map.Entry<LocalDate, Object>> dateGroup: dateGroupsList) {
			for (Map.Entry<LocalDate, Object> dateInfo : dateGroup) {
				String extractionDate = formatter.format(dateInfo.getKey());
				String extractionYear = extractionDate.split("\\/")[2];
				String extractionMonth = extractionDate.split("\\/")[1];
				String extractionDay = extractionDate.split("\\/")[0];
				FileSystemItem mainFile = FileSystemItem.ofPath(PersistentStorage.buildWorkingPath() + File.separator + "["+ extractionYear +"] - Combinazioni superenalotto.xlsx");
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
					int offset = getCellIndex(sheet, extractionDay);
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
					sheet.setColumnWidth(offset + 6, 5700);
					Cell cell = rowIterator.next().getCell(offset + 6);
					XSSFRichTextString results = new XSSFRichTextString();
					checkCombo(
						globalData,
						dataForTime,
						dateInfo.getKey(),
						system,
						SEStats.get(SEStatsDefaultDate).getWinningComboOf(dateInfo.getKey()),
						results,
						boldFont
					);
					results.append("\n");
					checkInHistory(
						historyData,
						dateInfo.getKey(),
						system,
						SEStats.get(SEStatsDefaultDate).getAllWinningCombos(),
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
		System.out.println("\nRisultati per tempo:");
		dataForTime.forEach((year, dataForMonth) -> {
			System.out.println("\t" + year + ":");
			dataForMonth.forEach((month, winningInfo) -> {
				System.out.println("\t\t" + month + ":");
				winningInfo.forEach((type, counter) -> {
					String label = SEStats.toLabel(type);
					System.out.println("\t\t\t" + label + ":" + SEStats.rightAlignedString(integerFormat.format(counter), 21 - label.length()));
				});
			});
		});
		System.out.println("\nRisultati globali:");
		globalData.forEach((key, combos) -> {
			String label = SEStats.toLabel(key);
			System.out.println("\t" + label + ":" + SEStats.rightAlignedString(integerFormat.format(combos.size()), 21 - label.length()));
		});
	}

	private static void checkCombo(
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
					extractionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALY), monthLabel -> new LinkedHashMap<>()
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
					results.append("  " + toLabel(combos.getKey()), boldFont);
					results.append(":" + "\n");
					for (List<Integer> combo : combos.getValue()) {
						results.append("    " +
							toString(combo, ", ", winningCombo) + "\n"
						);
					}
				}
			} else {
				results.append("nessuna vincita\n");
			}
		}
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
				results.append(": " + integerFormat.format(singleHistoryResult.getValue().size()) + "\n");
			}
		} else {
			results.append("nessuna vincita");
		}
	}

	private static String toString(Collection<Integer> combo, String separator, Collection<Integer> numbers) {
		return String.join(
			separator,
			combo.stream()
		    .map(val -> {
		    	return val.toString();
		    })
		    .collect(Collectors.toList())
		);
	}

	private static String toString(Collection<Integer> combo, String separator) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	private static String toLabel(Integer hit) {
		if (hit == 2) {
			return "Ambo";
		}
		if (hit == 3) {
			return "Terno";
		}
		if (hit == 4) {
			return "Quaterna";
		}
		if (hit == 5) {
			return "Cinquina";
		}
		if (hit == 6) {
			return "Tombola";
		}
		throw new IllegalArgumentException();
	}

	private static int getCellIndex(Sheet sheet, Date localDate) {
		return getCellIndex(sheet, 0, localDate);
	}

	private static int getCellIndex(Sheet sheet, int headerIndex, Date date) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (CellType.NUMERIC.equals(cell.getCellType()) && date.compareTo(cell.getDateCellValue()) == 0 ) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}

	private static int getCellIndex(Sheet sheet, String localDate) {
		return getCellIndex(sheet, 0, localDate);
	}

	private static int getCellIndex(Sheet sheet, int headerIndex, String dayAsString) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (CellType.STRING.equals(cell.getCellType()) && dayAsString.equals(cell.getStringCellValue())) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}

}
