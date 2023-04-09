package org.rg.game.lottery.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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

	private final DateFormat dateFmt = new SimpleDateFormat("yyyy dd MMMM", Locale.ITALY);
	private final DateFormat defaultDateFmt = new SimpleDateFormat("dd/MM/yyyy");
	private final DateFormat defaultDateFmtForFile = new SimpleDateFormat("yyyyMMdd");
	private Date startDate;

	private List<Map.Entry<String, Integer>> extractedNumberPairCounters;
	private List<Map.Entry<Integer, Integer>> extractedNumberCountersFromMostExtractedCouple;
	private List<Map.Entry<String, Integer>> extractedNumberCounters;
	private List<Map.Entry<String, Integer>> counterOfAbsencesFromCompetitions;
	private List<Map.Entry<String, Integer>> counterOfMaxAbsencesFromCompetitions;
	private Map<Date, List<Integer>> allWinningCombos;
	private Map<Date, List<Integer>> allWinningCombosWithJollyAndSuperstar;

	private SEStats(String startDate) {
		init(startDate);
	}

	public final static SEStats get(String startDate) {
		return INSTANCES.computeIfAbsent(startDate, key -> new SEStats(startDate));
	}

	private void init(String startDate) {
		this.startDate = buildStartDate(startDate);
		Map<String, Integer> extractedNumberPairCountersMap = buildExtractedNumberPairCountersMap();
		Map<String, Integer> extractedNumberCountersMap = new LinkedHashMap<>();
		allWinningCombos = new LinkedHashMap<>();
		allWinningCombosWithJollyAndSuperstar = new LinkedHashMap<>();
		try {
			try {
				if (forceLoadingFromExcel) {
					throw new RuntimeException();
				}
				loadRawDataFromInternet(
					extractedNumberPairCountersMap,
					extractedNumberCountersMap
				);
			} catch (Throwable exc) {
				if (!forceLoadingFromExcel) {
					System.out.println("Unable to load data from Internet: " + exc.getMessage());
				}
				extractedNumberPairCountersMap = buildExtractedNumberPairCountersMap();
				extractedNumberCountersMap = new LinkedHashMap<>();
				loadRawDataFromExcel(
					extractedNumberPairCountersMap,
					extractedNumberCountersMap
				);
			}
			loadData(
				extractedNumberPairCountersMap,
				extractedNumberCountersMap
			);
		} catch (Throwable exc) {
			throw new RuntimeException(exc);
		}
		try {
			storeToExcel(
				PersistentStorage.buildWorkingPath()
			);
		} catch (IOException exc) {
			throw new RuntimeException(exc);
		}
		System.out.println("\nAll extraction data have been succesfully loaded\n\n");
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

	private String getExcelFileName() {
		return "[SE" + defaultDateFmtForFile.format(startDate) + "] - Archivio estrazioni.xlsx";
	}

	private void loadRawDataFromExcel(
		Map<String, Integer> extractedNumberPairCountersMap,
		Map<String, Integer> extractedNumberCountersMap
	) throws IOException {
		try (InputStream inputStream = new FileInputStream(PersistentStorage.buildWorkingPath() + File.separator + getExcelFileName());
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
						Integer counter = extractedNumberCountersMap.computeIfAbsent(number.toString(), key -> 0);
						extractedNumberCountersMap.put(number.toString(), ++counter);
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
					ComboHandler comboHandler = new ComboHandler(extractedCombo, 2);
					Collection<List<Integer>> allCouples = comboHandler.find(IntStream.range(0, comboHandler.getSize())
						.boxed().collect(Collectors.toList()), true).values();
					List<String> extractedCoupleCounters = allCouples.stream().map(couple ->
						String.join("-", couple.stream().map(Object::toString).collect(Collectors.toList()))
					).collect(Collectors.toList());
					for (String couple : extractedCoupleCounters) {
						extractedNumberPairCountersMap.put(couple, extractedNumberPairCountersMap.get(couple) + 1);
					}
				}
			}
		}

	}


	private void loadData(
		Map<String, Integer> extractedNumberPairCountersMap,
		Map<String, Integer> extractedNumberCountersMap
	) {
		Comparator<Map.Entry<?, Integer>> comparator = (c1, c2) -> c1.getValue().compareTo(c2.getValue());
		extractedNumberPairCounters = extractedNumberPairCountersMap.entrySet().stream().sorted(comparator.reversed()).collect(Collectors.toList());
		//extractedNumberPairCounters.stream().forEach(entry -> System.out.println(String.join("\t", Arrays.asList(entry.getKey().split("-"))) + "\t" + entry.getValue()));
		extractedNumberCounters = extractedNumberCountersMap.entrySet().stream().sorted(comparator.reversed()).collect(Collectors.toList());
		//extractedNumberCounters.stream().forEach(entry -> System.out.println(String.join("\t", Arrays.asList(entry.getKey().split("-"))) + "\t" + entry.getValue()));
		Map<Integer, Integer> extractedNumberFromMostExtractedCoupleMap = new LinkedHashMap<>();
		extractedNumberPairCounters.stream().forEach(entry -> {
			for (Integer number : Arrays.stream(entry.getKey().split("-")).map(Integer::parseInt).collect(Collectors.toList())) {
				Integer counter = extractedNumberFromMostExtractedCoupleMap.computeIfAbsent(number, key -> 0);
				extractedNumberFromMostExtractedCoupleMap.put(number, counter + entry.getValue());
			}
		});
		extractedNumberCountersFromMostExtractedCouple =
			extractedNumberFromMostExtractedCoupleMap.entrySet().stream().sorted(comparator.reversed()).collect(Collectors.toList());
		Map<String, Integer> counterOfAbsencesFromCompetitionsMap = new LinkedHashMap<>();
		Map<String, Integer> counterOfMaxAbsencesFromCompetitionsMap = new LinkedHashMap<>();
		new TreeMap<>(allWinningCombos).values().stream().forEach(extractedCombo -> {
			extractedCombo.stream().forEach(extractedNumber -> {
				counterOfAbsencesFromCompetitionsMap.put(String.valueOf(extractedNumber), 0);
			});
			counterOfAbsencesFromCompetitionsMap.entrySet().stream()
			.filter(entry -> !extractedCombo.contains(Integer.valueOf(entry.getKey())))
			.forEach(entry -> {
				Integer latestMaxCounter = entry.getValue() + 1;
				entry.setValue(latestMaxCounter);
				Integer maxCounter = counterOfMaxAbsencesFromCompetitionsMap.get(entry.getKey());
				if (maxCounter == null || maxCounter < latestMaxCounter) {
					counterOfMaxAbsencesFromCompetitionsMap.put(entry.getKey(), latestMaxCounter);
				}
			});
		});
		Comparator<Map.Entry<String, Integer>> doubleComparator = (itemOne, itemTwo) ->
		(itemOne.getValue() < itemTwo.getValue()) ? 1 :
			(itemOne.getValue() == itemTwo.getValue()) ?
				Integer.valueOf(itemOne.getKey()).compareTo(Integer.valueOf(itemTwo.getKey())) :
				-1;
		counterOfAbsencesFromCompetitions =
			counterOfAbsencesFromCompetitionsMap.entrySet().stream().sorted(doubleComparator).collect(Collectors.toList());
		counterOfMaxAbsencesFromCompetitions =
			counterOfMaxAbsencesFromCompetitionsMap.entrySet().stream().sorted(doubleComparator).collect(Collectors.toList());
	}


	private void loadRawDataFromInternet(
		Map<String, Integer> extractedNumberPairCountersMap,
		Map<String, Integer> extractedNumberCountersMap
	) throws ParseException, IOException {
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
							Integer counter = extractedNumberCountersMap.computeIfAbsent(numberAsString, key -> 0);
							extractedNumberCountersMap.put(numberAsString, ++counter);
							//System.out.print(extractedNumber + "\t");
						}
						Collections.sort(extractedCombo);
						allWinningCombos.put(extractionDate, extractedCombo);
						List<Integer> extractedComboWithJollyAndSuperstar = new ArrayList<>(extractedCombo);
						extractedComboWithJollyAndSuperstar.add(Integer.parseInt(tableRow.select("li[class=jolly]").first().text()));
						extractedComboWithJollyAndSuperstar.add(Integer.parseInt(tableRow.select("li[class=superstar]").first().text()));
						allWinningCombosWithJollyAndSuperstar.put(extractionDate, extractedComboWithJollyAndSuperstar);
						ComboHandler comboHandler = new ComboHandler(extractedCombo, 2);
						Collection<List<Integer>> allCouples = comboHandler.find(IntStream.range(0, comboHandler.getSize())
							.boxed().collect(Collectors.toList()), true).values();
						List<String> extractedCoupleCounters = allCouples.stream().map(couple ->
							String.join("-", couple.stream().map(Object::toString).collect(Collectors.toList()))
						).collect(Collectors.toList());
						for (String couple : extractedCoupleCounters) {
							extractedNumberPairCountersMap.put(couple, extractedNumberPairCountersMap.get(couple) + 1);
						}
					}
				}
			}
		}
	}


	public void storeToExcel(String basePath) throws IOException {
		try (FileOutputStream outputStream = new FileOutputStream(basePath + File.separator +  getExcelFileName());
			SimpleWorkbookTemplate template = new SimpleWorkbookTemplate(true);
		) {
			Sheet sheet = template.getOrCreateSheet("Numeri più estratti", true);
			sheet.setColumnWidth(0, 25 * 112);
			sheet.setColumnWidth(1, 25 * 192);
			template.createHeader(true, Arrays.asList("Numero", "Conteggio estrazioni"));
			CellStyle defaultNumberCellStyle = template.getOrCreateStyle(template.getWorkbook(), "0");
			defaultNumberCellStyle.setAlignment(HorizontalAlignment.CENTER);
			CellStyle altOne = template.getWorkbook().createCellStyle();
			altOne.cloneStyleFrom(defaultNumberCellStyle);
			altOne.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			altOne.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
			CellStyle altTwo = template.getWorkbook().createCellStyle();
			altTwo.cloneStyleFrom(defaultNumberCellStyle);
			altTwo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			altTwo.setFillForegroundColor(IndexedColors.RED.getIndex());
			for (Map.Entry<String, Integer> extractionData : extractedNumberCounters) {
				template.addRow();
				template.addCell(Integer.parseInt(extractionData.getKey()), "0");
				template.addCell(extractionData.getValue(), "0");
			}
			sheet = template.getOrCreateSheet("Numeri più estratti per coppia", true);
			sheet.setColumnWidth(0, 25 * 112);
			sheet.setColumnWidth(1, 25 * 192);
			template.createHeader(true, Arrays.asList("Numero", "Conteggio presenze"));
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
			sheet = template.getOrCreateSheet("Numeri per ritardo massimo", true);
			sheet.setColumnWidth(0, 25 * 112);
			sheet.setColumnWidth(1, 25 * 240);
			template.createHeader(true, Arrays.asList("Numero", "Conteggio assenze massime"));
			for (Map.Entry<String, Integer> extractionData : counterOfMaxAbsencesFromCompetitions) {
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
						cell.setCellStyle(altOne);
					}
					if (i == 7) {
						cell.setCellStyle(altTwo);
					}
				}
			}
			template.getWorkbook().write(outputStream);
		}
	}


	public List<Integer> getExtractedNumberFromMostExtractedCoupleRank() {
		return extractedNumberCountersFromMostExtractedCouple.stream().map(entry -> entry.getKey()).collect(Collectors.toList());
	}

	public List<Integer> getExtractedNumberFromMostExtractedCoupleRankReversed() {
		List<Integer> reversed = getExtractedNumberFromMostExtractedCoupleRank();
		Collections.reverse(reversed);
		return reversed;
	}

	public List<Integer> getMostAbsentNumbersRank() {
		return counterOfAbsencesFromCompetitions.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getMostAbsentNumbersRankReversed() {
		List<Integer> reversed = getMostAbsentNumbersRank();
		Collections.reverse(reversed);
		return reversed;
	}

	public List<Integer> getExtractedNumberRank() {
		return extractedNumberCounters.stream().map(entry -> Integer.parseInt(entry.getKey())).collect(Collectors.toList());
	}

	public List<Integer> getExtractedNumberRankReversed() {
		List<Integer> reversed = getExtractedNumberRank();
		Collections.reverse(reversed);
		return reversed;
	}

	public Map<Date, List<Integer>> getAllWinningCombos() {
		return allWinningCombos;
	}

	public Map<Date, List<Integer>> getAllWinningCombosWithJollyAndSuperstar() {
		return allWinningCombosWithJollyAndSuperstar;
	}

}
