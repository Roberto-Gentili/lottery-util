package org.rg.game.lottery.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SEStats {
	private static final Map<String, SEStats> INSTANCES;
	public static boolean forceLoadingFromExcel;

	static {
		SEStats.forceLoadingFromExcel =
				Boolean.parseBoolean(System.getenv().getOrDefault("se-stats.force-loading-from excel", "false"));
		INSTANCES = new ConcurrentHashMap<>();
	}

	private Collection<DataLoader> dataLoaders;
	private Collection<DataStorer> dataStorers;

	private final DateFormat dateFmt = new SimpleDateFormat("yyyy dd MMMM", Locale.ITALY);
	private final DateFormat defaultDateFmt = new SimpleDateFormat("dd/MM/yyyy");
	private final DateFormat defaultDateFmtForFile = new SimpleDateFormat("yyyyMMdd");
	private Date startDate;

	private List<Map.Entry<String, Integer>> extractedNumberPairCounters;
	private List<Map.Entry<Integer, Integer>> extractedNumberCountersFromMostExtractedCouple;
	private List<Map.Entry<String, Integer>> extractedNumberCounters;
	private List<Map.Entry<String, Integer>> counterOfAbsencesFromCompetitions;
	private List<Map.Entry<String, Integer>> absencesRecordFromCompetitions;
	private List<Map.Entry<String, Integer>> distanceFromAbsenceRecord;
	private List<Map.Entry<String, Integer>> distanceFromAbsenceRecordPercentage;
	private Map<Date, List<Integer>> allWinningCombos;
	private Map<Date, List<Integer>> allWinningCombosWithJollyAndSuperstar;

	private SEStats(String startDate) {
		init(startDate);
	}

	public final static SEStats get(String startDate) {
		return INSTANCES.computeIfAbsent(startDate, key -> new SEStats(startDate));
	}

	private void init(String startDate) {
		dataLoaders = Arrays.asList(
			new InternetDataLoader(),
			new FromExcelDataLoader()
		);
		dataStorers = Arrays.asList(
			new ToExcelDataStorerV1(),
			new ToExcelDataStorerV2()
		);
		this.startDate = buildStartDate(startDate);
		this.allWinningCombos = new LinkedHashMap<>();
		this.allWinningCombosWithJollyAndSuperstar = new LinkedHashMap<>();
		boolean dataLoaded = false;
		for (DataLoader dataLoader : dataLoaders) {
			try {
				if (dataLoaded = dataLoader.load()) {
					break;
				}
			} catch (Throwable exc) {
				System.out.println(dataLoader.getClass() + " in unable to load extractions data: " + exc.getMessage());
			}
		}
		if (!dataLoaded) {
			throw new RuntimeException("Unable to load data");
		}

		loadStats();
		System.out.println("\nAll extraction data have been succesfully loaded\n\n");
		for (DataStorer dataStorer : dataStorers) {
			try {
				if (dataStorer.store()) {

				} else {
					System.out.println(dataStorer.getClass() + " stored no data");
				}
			} catch (Throwable exc) {
				exc.printStackTrace();
				System.out.println(dataStorer.getClass() + " in unable to store extractions data: " + exc.getMessage());
			}
		}
	}

	private Date buildStartDate(String dateAsString) {
		try {
			return defaultDateFmt.parse(dateAsString);
		} catch (ParseException exc) {
			throw new RuntimeException(exc);
		}
	}

	private Map<String, Integer> buildExtractedNumberPairCountersMap() {
		ComboHandler comboHandler = new ComboHandler(IntStream.range(1, 91).boxed().collect(Collectors.toList()), 2);
		Collection<List<Integer>> allCouples = comboHandler.find(IntStream.range(0, comboHandler.getSize()).boxed().collect(Collectors.toList()), true).values();
		Map<String, Integer> extractedNumberPairCountersMap = allCouples.stream().map(couple ->
			String.join("-", couple.stream().map(Object::toString).collect(Collectors.toList()))
		).collect(Collectors.toMap(key -> key, value -> 0, (x, y) -> y, LinkedHashMap::new));
		return extractedNumberPairCountersMap;
	}

	private void loadStats() {
		Map<String, Integer> extractedNumberPairCountersMap = buildExtractedNumberPairCountersMap();
		Map<String, Integer> extractedNumberCountersMap = new LinkedHashMap<>();
		Map<String, Integer> counterOfAbsencesFromCompetitionsMap = new LinkedHashMap<>();
		Map<String, Integer> absencesRecordFromCompetitionsMap = new LinkedHashMap<>();
		new TreeMap<>(allWinningCombos).entrySet().forEach(dateAndExtractedCombo -> {
			List<Integer> extractedCombo = dateAndExtractedCombo.getValue();
			extractedCombo.stream().forEach(number -> {
				Integer counter = extractedNumberCountersMap.computeIfAbsent(number.toString(), key -> 0);
				extractedNumberCountersMap.put(number.toString(), ++counter);
			});
			ComboHandler comboHandler = new ComboHandler(extractedCombo, 2);
			Collection<List<Integer>> allCouples = comboHandler.find(IntStream.range(0, comboHandler.getSize())
				.boxed().collect(Collectors.toList()), true).values();
			List<String> extractedCoupleCounters = allCouples.stream().map(couple ->
				String.join("-", couple.stream().map(Object::toString).collect(Collectors.toList()))
			).collect(Collectors.toList());
			for (String couple : extractedCoupleCounters) {
				extractedNumberPairCountersMap.put(couple, extractedNumberPairCountersMap.get(couple) + 1);
			}
			extractedCombo.stream().forEach(extractedNumber -> {
				counterOfAbsencesFromCompetitionsMap.put(String.valueOf(extractedNumber), 0);
			});
			counterOfAbsencesFromCompetitionsMap.entrySet().stream()
			.filter(entry -> !extractedCombo.contains(Integer.valueOf(entry.getKey())))
			.forEach(entry -> {
				Integer latestMaxCounter = entry.getValue() + 1;
				entry.setValue(latestMaxCounter);
				Integer maxCounter = absencesRecordFromCompetitionsMap.get(entry.getKey());
				if (maxCounter == null || maxCounter < latestMaxCounter) {
					absencesRecordFromCompetitionsMap.put(entry.getKey(), latestMaxCounter);
				}
			});
		});

		Comparator<Map.Entry<?, Integer>> integerComparator = (c1, c2) -> c1.getValue().compareTo(c2.getValue());
		Comparator<Map.Entry<String, Integer>> doubleIntegerComparator = (itemOne, itemTwo) ->
		(itemOne.getValue() < itemTwo.getValue()) ? -1 :
			(itemOne.getValue() == itemTwo.getValue()) ?
				Integer.valueOf(itemOne.getKey()).compareTo(Integer.valueOf(itemTwo.getKey())) :
				1;
		Comparator<Map.Entry<String, Integer>> doubleIntegerComparatorReversed = (itemOne, itemTwo) ->
		(itemOne.getValue() < itemTwo.getValue()) ? 1 :
			(itemOne.getValue() == itemTwo.getValue()) ?
				Integer.valueOf(itemOne.getKey()).compareTo(Integer.valueOf(itemTwo.getKey())) :
				-1;
		extractedNumberPairCounters = extractedNumberPairCountersMap.entrySet().stream().sorted(integerComparator.reversed()).collect(Collectors.toList());
		//extractedNumberPairCounters.stream().forEach(entry -> System.out.println(String.join("\t", Arrays.asList(entry.getKey().split("-"))) + "\t" + entry.getValue()));
		extractedNumberCounters = extractedNumberCountersMap.entrySet().stream().sorted(integerComparator.reversed()).collect(Collectors.toList());
		//extractedNumberCounters.stream().forEach(entry -> System.out.println(String.join("\t", Arrays.asList(entry.getKey().split("-"))) + "\t" + entry.getValue()));
		Map<Integer, Integer> extractedNumberFromMostExtractedCoupleMap = new LinkedHashMap<>();
		extractedNumberPairCounters.stream().forEach(entry -> {
			for (Integer number : Arrays.stream(entry.getKey().split("-")).map(Integer::parseInt).collect(Collectors.toList())) {
				Integer counter = extractedNumberFromMostExtractedCoupleMap.computeIfAbsent(number, key -> 0);
				extractedNumberFromMostExtractedCoupleMap.put(number, counter + entry.getValue());
			}
		});
		extractedNumberCountersFromMostExtractedCouple =
			extractedNumberFromMostExtractedCoupleMap.entrySet().stream().sorted(integerComparator.reversed()).collect(Collectors.toList());
		counterOfAbsencesFromCompetitions =
			counterOfAbsencesFromCompetitionsMap.entrySet().stream().sorted(doubleIntegerComparatorReversed).collect(Collectors.toList());
		absencesRecordFromCompetitions =
			absencesRecordFromCompetitionsMap.entrySet().stream().sorted(doubleIntegerComparator).collect(Collectors.toList());
		distanceFromAbsenceRecord = new ArrayList<>();
		distanceFromAbsenceRecordPercentage = new ArrayList<>();
		counterOfAbsencesFromCompetitions.stream().forEach(entry -> {
			Integer absenceRecord = absencesRecordFromCompetitionsMap.get(entry.getKey());
			Integer distanceFromRecord = entry.getValue() - absenceRecord;
			distanceFromAbsenceRecord.add(new AbstractMap.SimpleEntry(entry.getKey(), distanceFromRecord));
			distanceFromAbsenceRecordPercentage.add(new AbstractMap.SimpleEntry(entry.getKey(), (distanceFromRecord * 100) / (double)absenceRecord));
		});
	}

	public List<Integer> getExtractedNumberFromMostExtractedCoupleRank() {
		return extractedNumberCountersFromMostExtractedCouple.stream().map(entry -> entry.getKey()).collect(Collectors.toList());
	}

	public List<Integer> getExtractedNumberFromMostExtractedCoupleRankReversed() {
		return toReversed(getExtractedNumberFromMostExtractedCoupleRank());
	}

	public List<Integer> getMostAbsentNumbersRank() {
		return counterOfAbsencesFromCompetitions.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getMostAbsentNumbersRankReversed() {
		return toReversed(getMostAbsentNumbersRank());
	}

	public List<Integer> getExtractedNumberRank() {
		return extractedNumberCounters.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getExtractedNumberRankReversed() {
		return toReversed(getExtractedNumberRank());
	}

	public List<Integer> getCounterOfAbsencesFromCompetitionsRank() {
		return counterOfAbsencesFromCompetitions.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getCounterOfAbsencesFromCompetitionsRankReversed() {
		return toReversed(getCounterOfAbsencesFromCompetitionsRank());
	}

	public List<Integer> getDistanceFromAbsenceRecordRank() {
		return distanceFromAbsenceRecord.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getDistanceFromAbsenceRecordRankReversed() {
		return toReversed(getDistanceFromAbsenceRecordRank());
	}

	public List<Integer> getDistanceFromAbsenceRecordPercentageRank() {
		return distanceFromAbsenceRecordPercentage.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getDistanceFromAbsenceRecordPercentageRankReversed() {
		return toReversed(getDistanceFromAbsenceRecordPercentageRank());
	}

	public Integer getCounterOfAbsencesFromCompetitionsFor(Object number) {
		return getStatFor(counterOfAbsencesFromCompetitions, number);
	}

	public Integer getAbsenceRecordFromCompetitionsFor(Object number) {
		return getStatFor(absencesRecordFromCompetitions, number);
	}

	public Integer getExtractedNumberCountersFromMostExtractedCoupleFor(Object number) {
		return getStatFor(extractedNumberCountersFromMostExtractedCouple, number);
	}

	public Integer getDistanceFromAbsenceRecordFor(Object number) {
		return getStatFor(distanceFromAbsenceRecord, number);
	}

	public Double getDistanceFromAbsenceRecordPercentageFor(Object number) {
		return getStatFor(distanceFromAbsenceRecordPercentage, number);
	}

	public Map<Date, List<Integer>> getAllWinningCombos() {
		return allWinningCombos;
	}

	private <N extends Number> List<N> toReversed(List<N> source) {
		List<N> reversed = new ArrayList<>(source);
		Collections.reverse(reversed);
		return reversed;
	}

	private <N extends Number> N getStatFor(Collection<?> stats, Object numberObject) {
		Integer number = numberObject instanceof String ?
			Integer.parseInt((String)numberObject) :
			(Integer)numberObject;
		for (Object obj : stats) {
			Map.Entry<?, Integer> extractionData = (Map.Entry<?, Integer>)obj;
			Object key = extractionData.getKey();
			Integer iteratedNumber = key instanceof String ?
				Integer.parseInt((String)key) :
				(Integer)key;
			if (iteratedNumber.equals(number)) {
				return (N)extractionData.getValue();
			}
		}
		return null;
	}

	public Map<Date, List<Integer>> getAllWinningCombosWithJollyAndSuperstar() {
		return allWinningCombosWithJollyAndSuperstar;
	}

	private static interface DataLoader {

		public boolean load() throws Throwable;

	}

	private static interface DataStorer {

		public boolean store() throws Throwable;

	}

	private class InternetDataLoader implements DataLoader {

		@Override
		public boolean load() throws Throwable {
			if (forceLoadingFromExcel) {
				return false;
			}
			int startYear = 2009;
			int endYear = Calendar.getInstance().get(Calendar.YEAR);
			System.out.println();
			for (int year : IntStream.range(startYear, (endYear + 1)).map(i -> (endYear + 1) - i + startYear - 1).toArray()) {
				System.out.println("Loading all extraction data of " + year);
				Document doc = Jsoup.connect("https://www.superenalotto.net/estrazioni/" + year).get();
				Element table = doc.select("table[class=resultsTable table light]").first();
				Iterator<Element> itr = table.select("tr").iterator();
				while (itr.hasNext()) {
					Element tableRow = itr.next();
					Elements dateCell = tableRow.select("td[class=date m-w60 m-righty]");
					if (!dateCell.isEmpty()) {
						Date extractionDate = dateFmt.parse(year + dateCell.iterator().next().textNodes().get(0).text());
						if (extractionDate.compareTo(startDate) >= 0) {
							//System.out.print(defaultFmt.format(fmt.parse(year + dateCell.iterator().next().textNodes().get(0).text())) + "\t");
							List<Integer> extractedCombo = new ArrayList<>();
							for (Element number : tableRow.select("ul[class=balls]").first().children()) {
								String numberAsString = number.text();
								Integer extractedNumber = Integer.parseInt(numberAsString);
								extractedCombo.add(extractedNumber);
								//System.out.print(extractedNumber + "\t");
							}
							Collections.sort(extractedCombo);
							allWinningCombos.put(extractionDate, extractedCombo);
							List<Integer> extractedComboWithJollyAndSuperstar = new ArrayList<>(extractedCombo);
							extractedComboWithJollyAndSuperstar.add(Integer.parseInt(tableRow.select("li[class=jolly]").first().text()));
							extractedComboWithJollyAndSuperstar.add(Integer.parseInt(tableRow.select("li[class=superstar]").first().text()));
							allWinningCombosWithJollyAndSuperstar.put(extractionDate, extractedComboWithJollyAndSuperstar);
						}
					}
				}
			}
			return true;
		}

	}

	private class FromExcelDataLoader implements DataLoader {

		@Override
		public boolean load() throws Throwable {
			try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + new ToExcelDataStorerV1().getFileName());
				Workbook workbook = new XSSFWorkbook(inputStream);
			) {
				Sheet sheet = workbook.getSheet("Storico estrazioni");
				Iterator<Row> rowIterator = sheet.rowIterator();
				//Skipping header
				rowIterator.next();
				while (rowIterator.hasNext()) {
					Row row = rowIterator.next();
					Iterator<Cell> numberIterator = row.cellIterator();
					Date extractionDate = numberIterator.next().getDateCellValue();
					if (extractionDate.compareTo(startDate) >= 0) {
						List<Integer> extractedCombo = new ArrayList<>();
						while (numberIterator.hasNext()) {
							Integer number = (int)numberIterator.next().getNumericCellValue();
							extractedCombo.add(number);
							if (extractedCombo.size() == 6) {
								break;
							}
						}
						Collections.sort(extractedCombo);
						allWinningCombos.put(extractionDate, extractedCombo);
						List<Integer> extractedComboWithJollyAndSuperstar = new ArrayList<>(extractedCombo);
						extractedComboWithJollyAndSuperstar.add((int)numberIterator.next().getNumericCellValue());
						extractedComboWithJollyAndSuperstar.add((int)numberIterator.next().getNumericCellValue());
						allWinningCombosWithJollyAndSuperstar.put(extractionDate, extractedComboWithJollyAndSuperstar);
					}
				}
			}
			return true;
		}
	}

	private class ToExcelDataStorerV1 implements DataStorer {

		private String getFileName() {
			return "[SE" + defaultDateFmtForFile.format(startDate) + "] - Archivio estrazioni e statistiche v1.xlsx";
		}

		@Override
		public boolean store() throws Throwable {
			try (FileOutputStream outputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator +  getFileName());
				SimpleWorkbookTemplate template = new SimpleWorkbookTemplate(true);
			) {
				CellStyle defaultNumberCellStyle = template.getOrCreateStyle(
					HorizontalAlignment.CENTER,
					"0"
				);
				CellStyle yellowBackground = template.getOrCreateStyle(
					"yellowBackgroundStyle",
					defaultNumberCellStyle,
					FillPatternType.SOLID_FOREGROUND,
					IndexedColors.YELLOW
				);
				CellStyle redBackground = template.getOrCreateStyle(
					"redBackgroundStyle",
					defaultNumberCellStyle,
					FillPatternType.SOLID_FOREGROUND,
					IndexedColors.RED
				);

				Sheet sheet = template.getOrCreateSheet("Numeri più estratti", true);
				sheet.setColumnWidth(0, 25 * 112);
				sheet.setColumnWidth(1, 25 * 192);
				template.createHeader(true, Arrays.asList("Numero", "Conteggio estrazioni"));
				for (Map.Entry<String, Integer> extractionData : extractedNumberCounters) {
					template.addRow();
					template.addCell(Integer.parseInt(extractionData.getKey()), "0");
					template.addCell(extractionData.getValue(), "0");
				}
				sheet = template.getOrCreateSheet("Numeri più estratti per coppia", true);
				sheet.setColumnWidth(0, 25 * 112);
				sheet.setColumnWidth(1, 25 * 192);
				template.createHeader(true, Arrays.asList("Numero", "Conteggio presenze nelle coppie più estratte"));
				for (Map.Entry<Integer, Integer> extractionData : extractedNumberCountersFromMostExtractedCouple) {
					template.addRow();
					template.addCell(extractionData.getKey(), "0");
					template.addCell(extractionData.getValue(), "0");
				}
				sheet = template.getOrCreateSheet("Coppie più estratte", true);
				sheet.setColumnWidth(0, 25 * 112);
				sheet.setColumnWidth(1, 25 * 112);
				sheet.setColumnWidth(2, 25 * 208);
				template.createHeader(true, Arrays.asList("1° numero", "2° numero", "Conteggio estrazioni"));
				for (Map.Entry<String, Integer> extractedNumberPairCounter : extractedNumberPairCounters) {
					String[] numbers = extractedNumberPairCounter.getKey().split("-");
					template.addRow();
					template.addCell(Integer.parseInt(numbers[0]), "0");
					template.addCell(Integer.parseInt(numbers[1]), "0");
					template.addCell(extractedNumberPairCounter.getValue(), "0");
				}
				sheet = template.getOrCreateSheet("Numeri ritardatari", true);
				sheet.setColumnWidth(0, 25 * 112);
				sheet.setColumnWidth(1, 25 * 176);
				template.createHeader(true, Arrays.asList("Numero", "Conteggio assenze"));
				for (Map.Entry<String, Integer> extractionData : counterOfAbsencesFromCompetitions) {
					template.addRow();
					template.addCell(Integer.parseInt(extractionData.getKey()), "0");
					template.addCell(extractionData.getValue(), "0");
				}
				sheet = template.getOrCreateSheet("Numeri più frequenti", true);
				sheet.setColumnWidth(0, 25 * 112);
				sheet.setColumnWidth(1, 25 * 256);
				template.createHeader(true, Arrays.asList("Numero", "Conteggio assenze massime"));
				for (Map.Entry<String, Integer> extractionData : absencesRecordFromCompetitions) {
					template.addRow();
					template.addCell(Integer.parseInt(extractionData.getKey()), "0");
					template.addCell(extractionData.getValue(), "0");
				}
				sheet = template.getOrCreateSheet("Storico estrazioni", true);
				sheet.setColumnWidth(0, 25 * 112);
				sheet.setColumnWidth(1, 25 * 112);
				sheet.setColumnWidth(2, 25 * 112);
				sheet.setColumnWidth(3, 25 * 112);
				sheet.setColumnWidth(4, 25 * 112);
				sheet.setColumnWidth(5, 25 * 112);
				sheet.setColumnWidth(6, 25 * 112);
				sheet.setColumnWidth(7, 25 * 112);
				sheet.setColumnWidth(8, 25 * 112);
				template.createHeader(true, Arrays.asList("Data", "1° numero", "2° numero", "3° numero", "4° numero", "5° numero", "6° numero", "Jolly", "Superstar"));
				for (Map.Entry<Date, List<Integer>> extractionData : allWinningCombosWithJollyAndSuperstar.entrySet()) {
					template.addRow();
					template.addCell(extractionData.getKey()).getCellStyle().setAlignment(HorizontalAlignment.LEFT);
					List<Integer> extractedNumers = extractionData.getValue();
					for (int i = 0; i < extractedNumers.size(); i++) {
						Cell cell = template.addCell(extractedNumers.get(i), "0");
						if (i == 6) {
							cell.setCellStyle(yellowBackground);
						}
						if (i == 7) {
							cell.setCellStyle(redBackground);
						}
					}
				}
				template.getWorkbook().write(outputStream);
			}
			return true;
		}

	}

	private class ToExcelDataStorerV2 implements DataStorer {

		private String getFileName() {
			return "[SE" + defaultDateFmtForFile.format(startDate) + "] - Archivio estrazioni e statistiche v2.xlsx";
		}

		@Override
		public boolean store() throws Throwable {
			try (FileOutputStream outputStream = new FileOutputStream(PersistentStorage.buildWorkingPath() + File.separator +  getFileName());
				SimpleWorkbookTemplate template = new SimpleWorkbookTemplate(true);
			) {
				CellStyle defaultNumberCellStyle = template.getOrCreateStyle(
					HorizontalAlignment.CENTER,
					"0"
				);
				CellStyle percentageNumberStyle = template.getOrCreateStyle(
					defaultNumberCellStyle,
					"0.00%"
				);
				CellStyle yellowBackground = template.getOrCreateStyle(
					"yellowBackgroundStyle",
					defaultNumberCellStyle,
					FillPatternType.SOLID_FOREGROUND,
					IndexedColors.YELLOW
				);
				CellStyle redBackground = template.getOrCreateStyle(
					"redBackgroundStyle",
					defaultNumberCellStyle,
					FillPatternType.SOLID_FOREGROUND,
					IndexedColors.RED
				);

				Sheet sheet = template.getOrCreateSheet("Statistiche per numero", true);
				sheet.setColumnWidth(0, 25 * 128);
				sheet.setColumnWidth(1, 25 * 232);
				sheet.setColumnWidth(2, 25 * 432);
				sheet.setColumnWidth(3, 25 * 216);
				sheet.setColumnWidth(4, 25 * 208);
				sheet.setColumnWidth(5, 25 * 312);
				sheet.setColumnWidth(6, 25 * 416);
				template.createHeader(
					true,
					Arrays.asList(
						"Numero",
						"Conteggio estrazioni",
						"Conteggio presenze nelle coppie più estratte",
						"Conteggio assenze",
						"Record di assenze",
						"Distanza dal record di assenze",
						"Distanza dal record di assenze percentuale"
					)
				);
				for (Map.Entry<String, Integer> extractionData : extractedNumberCounters) {
					template.addRow();
					template.addCell(Integer.parseInt(extractionData.getKey()), "0");
					template.addCell(extractionData.getValue(), "0");
					template.addCell(getExtractedNumberCountersFromMostExtractedCoupleFor(extractionData.getKey()), "0");
					template.addCell(getCounterOfAbsencesFromCompetitionsFor(extractionData.getKey()), "0");
					template.addCell(getAbsenceRecordFromCompetitionsFor(extractionData.getKey()), "0");
					template.addCell(getDistanceFromAbsenceRecordFor(extractionData.getKey()), "0");
					template.addCell(
						getDistanceFromAbsenceRecordPercentageFor(extractionData.getKey()) /100
					).setCellStyle(percentageNumberStyle);
				}
				template.setAutoFilter();
				sheet = template.getOrCreateSheet("Coppie più estratte", true);
				sheet.setColumnWidth(0, 25 * 144);
				sheet.setColumnWidth(1, 25 * 144);
				sheet.setColumnWidth(2, 25 * 240);
				template.createHeader(true, Arrays.asList("1° numero", "2° numero", "Conteggio estrazioni"));
				for (Map.Entry<String, Integer> extractedNumberPairCounter : extractedNumberPairCounters) {
					String[] numbers = extractedNumberPairCounter.getKey().split("-");
					template.addRow();
					template.addCell(Integer.parseInt(numbers[0]), "0");
					template.addCell(Integer.parseInt(numbers[1]), "0");
					template.addCell(extractedNumberPairCounter.getValue(), "0");
				}
				template.setAutoFilter();
				sheet = template.getOrCreateSheet("Storico estrazioni", true);
				sheet.setColumnWidth(0, 25 * 144);
				sheet.setColumnWidth(1, 25 * 144);
				sheet.setColumnWidth(2, 25 * 144);
				sheet.setColumnWidth(3, 25 * 144);
				sheet.setColumnWidth(4, 25 * 144);
				sheet.setColumnWidth(5, 25 * 144);
				sheet.setColumnWidth(6, 25 * 144);
				sheet.setColumnWidth(7, 25 * 144);
				sheet.setColumnWidth(8, 25 * 144);
				template.createHeader(true, Arrays.asList("Data", "1° numero", "2° numero", "3° numero", "4° numero", "5° numero", "6° numero", "Jolly", "Superstar"));
				for (Map.Entry<Date, List<Integer>> extractionData : allWinningCombosWithJollyAndSuperstar.entrySet()) {
					template.addRow();
					template.addCell(extractionData.getKey()).getCellStyle().setAlignment(HorizontalAlignment.LEFT);
					List<Integer> extractedNumers = extractionData.getValue();
					for (int i = 0; i < extractedNumers.size(); i++) {
						Cell cell = template.addCell(extractedNumers.get(i), "0");
						if (i == 6) {
							cell.setCellStyle(yellowBackground);
						}
						if (i == 7) {
							cell.setCellStyle(redBackground);
						}
					}
				}
				template.setAutoFilter();
				template.getWorkbook().write(outputStream);
			}
			return true;
		}

	}

}
