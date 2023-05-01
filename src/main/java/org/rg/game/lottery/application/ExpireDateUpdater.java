package org.rg.game.lottery.application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.PersistentStorage;

public class ExpireDateUpdater {
	static DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	static List<Map.Entry<List<String>, Integer>> updateInfos = Arrays.asList(
		//addUpdateInfo(computeIncrementationOfDays(1), "all")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Barella Roberta")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Bellacanzone Emanuele")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Berni Valentina")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Corinti Massimo")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Fusi Francesco")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Pistella Maria Anna")
		//addUpdateInfo(computeIncrementationOfWeeks(2), "Carrazza Alessandro", "Liberati Claudio")
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Oroni Paola")
		//addUpdateInfo(computeIncrementationOfWeeks(60), "Porta Danilo")
		//addUpdateInfo(computeIncrementationOfWeeks(20), "Tondini Andrea")
		/*addUpdateInfo(computeIncrementationOfWeeks(-1),
			"Berni Valentina",
			"Carrazza Alessandro",
			"Dante Marco",
			"Ingegneri Giuseppe",
			"Liberati Claudio",
			"Pistella Maria Anna"
		)*/
		//addUpdateInfo(computeIncrementationOfWeeks(4), "Pistella Federica")
	);

	public static void main(String[] args) {
		String destFileAbsolutePath = PersistentStorage.buildWorkingPath() + "\\Abbonamenti e altre informazioni.xlsx";
		File srcFile = new File(destFileAbsolutePath);
		File history = new File(srcFile.getParentFile() + "\\Storico abbonamenti");
		history.mkdirs();
		File backupFile = new File(history.getAbsolutePath() + "\\[" + datePattern.format(LocalDateTime.now()) + "] - " + srcFile.getName());
		srcFile.renameTo(backupFile);
		try (InputStream srcFileInputStream = new FileInputStream(backupFile); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
			Sheet sheet = workbook.getSheet("Abbonamenti");
			int nameColumnIndex = getCellIndex(sheet, "Nominativo");
			int expiryColumnIndex = getCellIndex(sheet, "Scadenza");
			Iterator<Row> rowIterator = sheet.rowIterator();
			rowIterator.next();
			System.out.println("Aggiornamento data scadenza abbonamento:\n");
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				String clientName = null;
				for (Map.Entry<List<String>, Integer> updateInfo : updateInfos) {
					if (updateInfo.getValue() == 0) {
						continue;
					}
					for (String name : updateInfo.getKey()) {
						if (!name.equalsIgnoreCase("all")) {
							Cell cell = row.getCell(nameColumnIndex);
							if (cell == null ||
								(clientName = cell.getStringCellValue()) == null ||
								clientName.isEmpty() ||
								!clientName.equalsIgnoreCase(name)
							) {
								continue;
							}
						}
						Cell expiryCell = row.getCell(expiryColumnIndex);
						if (expiryCell != null) {
							Date expiryDate = expiryCell.getDateCellValue();
							if (expiryDate != null) {
								LocalDate expiryLocalDate = expiryDate.toInstant()
								      .atZone(ZoneId.systemDefault())
								      .toLocalDate();
								LocalDate startExpiryDate = expiryLocalDate;
								if (LocalDate.now().compareTo(expiryLocalDate) > 0) {
									if (name.equalsIgnoreCase("all")) {
										continue;
									}
									expiryLocalDate = LocalDate.now();
								}
								if (updateInfo.getValue() > 0) {
									for (int i = 0; i < updateInfo.getValue(); i++) {
										expiryLocalDate = expiryLocalDate.plus(getIncrementDays(expiryLocalDate, true), ChronoUnit.DAYS);
									}
								} else {
									for (int i = updateInfo.getValue(); i < 0; i++) {
										expiryLocalDate = expiryLocalDate.minus(getIncrementDays(expiryLocalDate, false), ChronoUnit.DAYS);
									}
								}
								boolean expireSoon = expiryLocalDate.minus(7, ChronoUnit.DAYS).compareTo(LocalDate.now()) <= 0;
								System.out.println(
									(expireSoon ? "*" : "") + row.getCell(nameColumnIndex).getStringCellValue() + (expireSoon ? "*" : "") +" da " + startExpiryDate.format(TimeUtils.defaultLocalDateFormat) +
									" a " + (expireSoon ? "*" : "") +expiryLocalDate.format(TimeUtils.defaultLocalDateFormat) + (expireSoon ? "*" : "")
								);
								expiryCell.setCellValue(Date.from(expiryLocalDate.atStartOfDay().atZone(ZoneId.of(TimeUtils.DEFAULT_TIME_ZONE)).toInstant()));
							}
						}
					}
				}
			}
			try (FileOutputStream outputStream = new FileOutputStream(destFileAbsolutePath)) {
				XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
				workbook.write(outputStream);
			}
		} catch (Throwable exc) {
			exc.printStackTrace();
		}
	}

	private static int getCellIndex(Sheet sheet, String name) {
		return getCellIndex(sheet, 0, name);
	}

	private static int getCellIndex(Sheet sheet, int headerIndex, String name) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			if (CellType.STRING.equals(cell.getCellType()) && name.equalsIgnoreCase(cell.getStringCellValue()) ) {
				return cell.getColumnIndex();
			}
		}
		return -1;
	}

	static SimpleEntry<List<String>, Integer> addUpdateInfo(Integer incrementation, String... names) {
		return new AbstractMap.SimpleEntry<>(
			Arrays.asList(names), computeIncrementation(incrementation, 0)
		);
	}

	static Integer computeIncrementationOfDays(int days) {
		return computeIncrementation(days, 0);
	}

	static Integer computeIncrementationOfWeeks(int weeks) {
		return computeIncrementation(0, weeks);
	}

	static Integer computeIncrementation(int days, int weeks) {
		return days + (weeks * 3);
	}

	private static int getIncrementDays(LocalDate startDate, boolean positive) {
		int dayOfWeek = startDate.getDayOfWeek().getValue();
		return positive?
			(dayOfWeek == DayOfWeek.SATURDAY.getValue() ? 3 :
				(dayOfWeek == DayOfWeek.TUESDAY.getValue() ||
				dayOfWeek == DayOfWeek.THURSDAY.getValue() ||
				dayOfWeek == DayOfWeek.SUNDAY.getValue()) ?
					2 : 1) :
			(dayOfWeek == DayOfWeek.TUESDAY.getValue() ? 3 :
				(dayOfWeek == DayOfWeek.MONDAY.getValue() ||
				dayOfWeek == DayOfWeek.THURSDAY.getValue() ||
				dayOfWeek == DayOfWeek.SATURDAY.getValue()) ?
					2 : 1);
	}

}
