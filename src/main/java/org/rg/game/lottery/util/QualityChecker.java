package org.rg.game.lottery.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SEStats;

public class QualityChecker {

	static SimpleDateFormat standardDatePattern = new SimpleDateFormat("dd/MM/yyyy");
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(standardDatePattern.toPattern());

	public static void main(String[] args) throws IOException {
		check("11/04/2023");
	}

	private static void check(String extractionDate) throws IOException {
		FileSystemItem mainFile = FileSystemItem.ofPath(PersistentStorage.buildWorkingPath() + File.separator + "Abbonamenti e altre informazioni.xlsx");
		List<List<Integer>> system = new ArrayList<>();
		try (InputStream srcFileInputStream = mainFile.toInputStream(); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
			Sheet sheet = workbook.getSheet("Combinazioni superenalotto");
			int offset = getCellIndex(sheet, standardDatePattern.parse(extractionDate));
			if (offset < 0) {
				System.out.println("No combination to test for date " + extractionDate);
				return;
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
			SEStats.get("02/07/2009").checkQuality(system::iterator);
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

}
