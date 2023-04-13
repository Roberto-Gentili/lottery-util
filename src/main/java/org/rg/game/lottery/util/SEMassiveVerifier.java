package org.rg.game.lottery.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;

public class SEMassiveVerifier {

	protected static DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	static SimpleDateFormat standardDatePattern = new SimpleDateFormat("dd/MM/yyyy");
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(standardDatePattern.toPattern());

	public static void main(String[] args) throws IOException {
		check(
			forDate("11/02/2023", "today", false)
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
		while (startDate.compareTo(endDate) < 0) {
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
		Map<Integer,List<List<Integer>>> globalData = new LinkedHashMap<>();
		for (List<Map.Entry<LocalDate, Object>> dateGroup: dateGroupsList) {
			for (Map.Entry<LocalDate, Object> dateInfo : dateGroup) {
				String extractionDate = formatter.format(dateInfo.getKey());
				String extractionYear = extractionDate.split("\\/")[2];
				String extractionMonth = extractionDate.split("\\/")[1];
				String extractionDay = extractionDate.split("\\/")[0];
				FileSystemItem mainFile = FileSystemItem.ofPath(PersistentStorage.buildWorkingPath() + File.separator + "["+ extractionYear +"] - Combinazioni superenalotto.xlsx");
				List<List<Integer>> system = new ArrayList<>();
				try (InputStream srcFileInputStream = mainFile.toInputStream(); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
					Sheet sheet = workbook.getSheet(extractionMonth);
					if (sheet == null) {
						System.out.println("No sheet to test for date " + extractionMonth);
						continue;
					}
					int offset = getCellIndex(sheet, extractionDay);
					if (offset < 0) {
						System.out.println("No combination to test for date " + extractionDate);
						continue;
					}
					Iterator<Row> rowIterator = sheet.rowIterator();
					rowIterator.next();
					while (rowIterator.hasNext()) {
						Row row = rowIterator.next();
						List<Integer> currentCombo = new ArrayList<>();
						for (int i = 0; i < 6; i++) {
							Integer currentNumber = Integer.valueOf((int)row.getCell(offset + i).getNumericCellValue());
							currentCombo.add(currentNumber);
						}
						if (currentCombo.get(0) == 0) {
							break;
						}
						system.add(currentCombo);
					}
					System.out.println(
						check(
							globalData, extractionDate, system, SEStats.get("02/07/2009").getWinningComboOf(dateInfo.getKey())
						)
					);
				} catch (Throwable exc) {
					exc.printStackTrace();
				}
			}
		}
		System.out.println("\nRisultati globali:");
		globalData.forEach((key, combos) -> {
			String label = SEStats.toLabel(key);
			System.out.println("\t" + label + ":" + SEStats.rightAlignedString(integerFormat.format(combos.size()), 21 - label.length()));
		});
	}

	private static String check(
		Map<Integer,List<List<Integer>>> globalData,
		String extractionDate, List<List<Integer>> combosToBeChecked, List<Integer> winningCombo
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
				globalData.computeIfAbsent(hit, ht -> new ArrayList<>()).add(currentCombo);
			}
		}
		StringBuffer result = new StringBuffer();
		if (!winningCombo.isEmpty()) {
			if (!winningCombos.isEmpty()) {
				result.append("\n\nNumeri estratti per il *superenalotto* del " + extractionDate +": " + toString(winningCombo, ", ", hitNumbers) + "\n");
				for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
					result.append("\t*Combinazioni con " + toLabel(combos.getKey()).toLowerCase() + "*:" + "\n");
					for (List<Integer> combo : combos.getValue()) {
						result.append("\t\t" +
							toString(combo, "\t", winningCombo) + "\n"
						);
					}
				}
			} else {
				result.append("Nessuna vincita per il superenalotto del " + extractionDate + "\n");
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
