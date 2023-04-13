package org.rg.game.lottery.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
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
import org.rg.game.lottery.engine.PersistentStorage;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Verifier {

	static SimpleDateFormat standardDatePattern = new SimpleDateFormat("dd/MM/yyyy");
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(standardDatePattern.toPattern());

	public static void main(String[] args) throws IOException {
		List<Integer> winningNumbers =
			args.length > 0 ?
				Arrays.stream(args.length > 1 ? args : args[0].replace(" ", "").split(","))
				.map(Integer::valueOf).collect(Collectors.toList())
			: null;
		check(System.getenv().get("competitionName"), null, winningNumbers);
	}

	private static void check(String competionName, String extractionDate, List<Integer> winningCombo) throws IOException {
		if (winningCombo == null && competionName.equals("Superenalotto")) {
			URL url = new URL("https://www.gntn-pgd.it/gntn-info-web/rest/gioco/superenalotto/estrazioni/ultimoconcorso");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestProperty("accept", "application/json");
			InputStream responseStream = connection.getInputStream();
			Map<String,Object> data = new ObjectMapper().readValue(responseStream, Map.class);
			extractionDate = standardDatePattern.format(new Date((Long)data.get("dataEstrazione")));
			winningCombo = ((List<String>)((Map<String,Object>)data.get("combinazioneVincente")).get("estratti"))
				.stream().map(Integer::valueOf).collect(Collectors.toList());
			connection.disconnect();
		}
		if (extractionDate == null) {
			LocalDate startDate = LocalDate.now();
			if (competionName.equals("Superenalotto")) {
				while (!(startDate.getDayOfWeek().getValue() == DayOfWeek.SATURDAY.getValue() ||
					startDate.getDayOfWeek().getValue() == DayOfWeek.TUESDAY.getValue() ||
					startDate.getDayOfWeek().getValue() == DayOfWeek.THURSDAY.getValue())) {
					startDate = startDate.minus(1, ChronoUnit.DAYS);
				}
			} else if (competionName.equals("Million Day")) {
				if (LocalDateTime.now(ZoneId.of("Europe/Rome")).compareTo(LocalDate.now(ZoneId.of("Europe/Rome")).atTime(20, 30)) < 0) {
					startDate = startDate.minus(1, ChronoUnit.DAYS);
				}
			}
			extractionDate = formatter.format(startDate);
		}
		String extractionYear = extractionDate.split("\\/")[2];
		String extractionMonth = extractionDate.split("\\/")[1];
		String extractionDay = extractionDate.split("\\/")[0];
		FileSystemItem mainFile = FileSystemItem.ofPath(PersistentStorage.buildWorkingPath() + File.separator + "["+ extractionYear +"] - Combinazioni " + competionName + ".xlsx");
		try (InputStream srcFileInputStream = mainFile.toInputStream(); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
			Sheet sheet = workbook.getSheet(extractionMonth);
			int offset = getCellIndex(sheet, extractionDay);
			if (offset < 0) {
				System.out.println("No combination to test for date " + extractionDate);
				return;
			}
			Iterator<Row> rowIterator = sheet.rowIterator();
			rowIterator.next();
			Map<Integer,List<List<Integer>>> winningCombos = new TreeMap<>();
			Collection<Integer> hitNumbers = new LinkedHashSet<>();
			int comboCount = 1;
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				Integer hit = 0;
				List<Integer> currentCombo = new ArrayList<>();
				for (int i = 0; i < winningCombo.size(); i++) {
					Cell cell = row.getCell(offset + i);
					if (cell == null) {
						break;
					}
					Integer currentNumber = Integer.valueOf((int)cell.getNumericCellValue());
					currentCombo.add(currentNumber);
					if (winningCombo.contains(currentNumber)) {
						hitNumbers.add(currentNumber);
						hit++;
					}
				}
				if (currentCombo.isEmpty()) {
					break;
				}
				if (hit > 1) {
					winningCombos.computeIfAbsent(hit, ht -> new ArrayList<>()).add(currentCombo);
					System.out.println(comboCount + ") " + toString(currentCombo, "\t"));
				}
				comboCount++;
			}
			if (!winningCombo.isEmpty()) {
				System.out.println("\n\nNumeri estratti per il *" + competionName + "* del " + extractionDate +": " + toString(winningCombo, ", ", hitNumbers));
				if (!winningCombos.isEmpty()) {
					for (Map.Entry<Integer, List<List<Integer>>> combos: winningCombos.entrySet()) {
						System.out.println("\t*Combinazioni con " + toLabel(combos.getKey()).toLowerCase() + "*:");
						for (List<Integer> combo : combos.getValue()) {
							System.out.println("\t\t" +
								toString(combo, "\t", winningCombo)
							);
						}
					}
				} else {
					System.out.println("Nessuna vincita");
				}
				System.out.println();
			}
		} catch (Throwable exc) {
			exc.printStackTrace();
		}
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
