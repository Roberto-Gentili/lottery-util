package org.rg.game.lottery.util;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.TimeUtils;

public class SEQualityChecker {

	public static void main(String[] args) throws IOException {
		check(
			forDate(
				Shared.getSystemEnv(
					"startDate", "14/02/2023"
				), Shared.getSystemEnv(
					"endDate", "next+0"
				),
				true
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
		return dates;
	}

	private static void check(List<Map.Entry<LocalDate, Object>>... dateGroupsList) throws IOException {
		for (List<Map.Entry<LocalDate, Object>> dateGroup: dateGroupsList) {
			for (Map.Entry<LocalDate, Object> dateInfo : dateGroup) {
				String extractionDate = TimeUtils.defaultDateFormat.format(dateInfo.getKey());
				String extractionYear = extractionDate.split("\\/")[2];
				String extractionMonth = Shared.getMonth(extractionDate);
				String extractionDay = extractionDate.split("\\/")[0];
				FileSystemItem mainFile =
					Shared.getSystemsFile(extractionYear);
				List<List<Integer>> system = new ArrayList<>();
				try (InputStream srcFileInputStream = mainFile.toInputStream(); Workbook workbook = new XSSFWorkbook(srcFileInputStream);) {
					Sheet sheet = workbook.getSheet(extractionMonth);
					if (sheet == null) {
						System.out.println("No sheet named '" + extractionMonth + "' to test for date " + extractionDate);
						continue;
					}
					int offset = Shared.getCellIndex(sheet, extractionDay);
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
							Cell cell = row.getCell(offset + i);
							if (cell == null) {
								break;
							}
							Integer currentNumber = Integer.valueOf((int)cell.getNumericCellValue());
							currentCombo.add(currentNumber);
						}
						if (currentCombo.isEmpty() || currentCombo.get(0) == 0) {
							break;
						}
						system.add(currentCombo);
					}
					System.out.println("\nAnalisi del sistema del " + extractionDate + ":" );
					Map<String, Object> report = Shared.getSEStats().checkQuality(system::iterator);
					if ((boolean)dateInfo.getValue()) {
						System.out.println("\t" + ((String)report.get("report.detail")).replace("\n", "\n\t"));
					}
					System.out.println("\t" + ((String)report.get("report.summary")).replace("\n", "\n\t"));
				} catch (Throwable exc) {
					exc.printStackTrace();
				}
			}
		}
	}

}
