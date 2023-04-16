package org.rg.game.lottery.util;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;

class Shared {

	static DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	static SimpleDateFormat standardDatePattern = new SimpleDateFormat("dd/MM/yyyy");
	static DateTimeFormatter formatter = DateTimeFormatter.ofPattern(standardDatePattern.toPattern());
	static String SEStatsDefaultDate = new SELotteryMatrixGeneratorEngine().getDefaultExtractionArchiveStartDate();

	static String capitalizeFirstCharacter(String value) {
		return Character.toString(value.charAt(0)).toUpperCase()
		+ value.substring(1, value.length());
	}

	static LocalDate convert(String dateAsString) {
		if (dateAsString.equals("today")) {
			return LocalDateTime.now(ZoneId.of("Europe/Rome")).toLocalDate();
		}
		return LocalDate.parse(dateAsString, Shared.formatter);
	}

	static String getMonth(String date) {
		return getMonth(LocalDate.parse(date, formatter));
	}

	static String getMonth(LocalDate date) {
		return capitalizeFirstCharacter(
			date.getMonth().getDisplayName(TextStyle.FULL, Locale.ITALY)
		);
	}

	static int getCellIndex(Sheet sheet, Date localDate) {
		return getCellIndex(sheet, 0, localDate);
	}

	static int getCellIndex(Sheet sheet, int headerIndex, Date date) {
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

	static int getCellIndex(Sheet sheet, String localDate) {
		return getCellIndex(sheet, 0, localDate);
	}

	static int getCellIndex(Sheet sheet, int headerIndex, String dayAsString) {
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

	static String toString(Collection<Integer> combo, String separator) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	static String toLabel(Integer hit) {
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

	static FileSystemItem getSystemsFile(String extractionYear) {
		return FileSystemItem.ofPath(PersistentStorage.buildWorkingPath() +
			File.separator + "[SE]["+ extractionYear +"] - Sistemi.xlsx");
	}

}
