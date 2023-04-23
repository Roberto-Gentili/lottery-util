package org.rg.game.lottery.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SELotteryMatrixGeneratorEngine extends LotteryMatrixGeneratorAbstEngine {
	private static final List<Map<String,List<Integer>>> allChosenNumbers;
	private static final List<Map<String,List<Integer>>> allDiscardedNumbers;

	static {
		allChosenNumbers = new ArrayList<>();
		allDiscardedNumbers = new ArrayList<>();
	}

	@Override
	public LocalDate computeNextExtractionDate(LocalDate startDate, boolean incrementIfExpired) {
		if (incrementIfExpired) {
			while (LocalDateTime.now(ZoneId.of(SEStats.DEFAULT_TIME_ZONE)).compareTo(
				LocalDateTime.now(ZoneId.of(SEStats.DEFAULT_TIME_ZONE)).with(startDate).withHour(19).withMinute(0).withSecond(0).withNano(0)
			) > 0) {
				startDate = startDate.plus(1, ChronoUnit.DAYS);
			}
		}
		while (!(startDate.getDayOfWeek().getValue() == DayOfWeek.SATURDAY.getValue() ||
			startDate.getDayOfWeek().getValue() == DayOfWeek.TUESDAY.getValue() ||
			startDate.getDayOfWeek().getValue() == DayOfWeek.THURSDAY.getValue())) {
			startDate = startDate.plus(1, ChronoUnit.DAYS);
		}
		return startDate;
	}

	@Override
	protected int getIncrementDays(LocalDate startDate) {
		return startDate.getDayOfWeek().getValue() == DayOfWeek.SATURDAY.getValue() ? 3 : 2;
	}

	@Override
	protected List<LocalDate> forWeekOf(LocalDate dayOfWeek) {
		LocalDate nextWeekStart = dayOfWeek.with(DayOfWeek.TUESDAY);
		List<LocalDate> dates = new ArrayList<>();
		dates.add(nextWeekStart);
		dates.add(nextWeekStart.plus(getIncrementDays(nextWeekStart), ChronoUnit.DAYS));
		dates.add(dates.get(1).plus(getIncrementDays(nextWeekStart), ChronoUnit.DAYS));
		return dates;
	}

	@Override
	public Map<String, Object> adjustSeed() {
		Map.Entry<LocalDate, Long> seedRecord = SEStats.get("03/12/1997", simpleDateFormatter.format(extractionDate)).getSeedData(extractionDate);
		random = new Random(seedRecord.getValue());
		buildComboIndexSupplier();
		Map<String, Object> seedData = new LinkedHashMap<>();
		seedData.put("seed", seedRecord.getValue());
		seedData.put("seedStartDate", seedRecord.getKey());
		return seedData;
	}


	@Override
	protected List<Map<String,List<Integer>>> getAllChosenNumbers() {
		return allChosenNumbers;
	}

	@Override
	protected List<Map<String,List<Integer>>> getAllDiscardedNumbers() {
		return allDiscardedNumbers;
	}

	@Override
	protected Function<String, Function<Integer, Function<Integer, Iterator<Integer>>>> getNumberGeneratorFactory() {
		return generatorType-> leftBound -> rightBound -> {
			if (NumberProcessor.RANDOM_KEY.equals(generatorType)) {
				return random.ints(leftBound , rightBound + 1).iterator();
			} else if (NumberProcessor.MOST_EXTRACTED_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getExtractedNumberRank(), leftBound, rightBound);
			} else if (NumberProcessor.MOST_EXTRACTED_COUPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getExtractedNumberFromMostExtractedCoupleRank(), leftBound, rightBound);
			} else if (NumberProcessor.MOST_EXTRACTED_TRIPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getExtractedNumberFromMostExtractedTripleRank(), leftBound, rightBound);
			} else if (NumberProcessor.LESS_EXTRACTED_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getExtractedNumberRankReversed(), leftBound, rightBound);
			} else if (NumberProcessor.LESS_EXTRACTED_COUPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getExtractedNumberFromMostExtractedCoupleRankReversed(), leftBound, rightBound);
			} else if (NumberProcessor.LESS_EXTRACTED_TRIPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getExtractedNumberFromMostExtractedTripleRankReversed(), leftBound, rightBound);
			} else if (NumberProcessor.NEAREST_FROM_RECORD_ABSENCE_PERCENTAGE_KEY.equals(generatorType)) {
				return new BoundedIterator(getSEStats().getDistanceFromAbsenceRecordPercentageRankReversed(), leftBound, rightBound);
			}
			throw new IllegalArgumentException("Unvalid generator type");
		};
	}

	@Override
	public String getDefaultExtractionArchiveStartDate() {
		return "02/07/2009";
	}

	@Override
	public String getDefaultExtractionArchiveForSeedStartDate() {
		return "03/12/1997";
	}

	@Override
	public Map<String, Object> testEffectiveness(String filterAsString, List<Integer> numbers, boolean fineLog) {
		filterAsString = preProcess(filterAsString);
		Predicate<List<Integer>> combinationFilter = CombinationFilterFactory.INSTANCE.parse(filterAsString, fineLog);
		Set<Entry<Date, List<Integer>>> allWinningCombos = getSEStats().getAllWinningCombos().entrySet();
		int discardedFromHistory = 0;
		System.out.println("Starting filter analysis\n");
		Collection<Integer> comboSums = new TreeSet<>();
		for (Map.Entry<Date, List<Integer>> comboForDate : allWinningCombos) {
			if (!combinationFilter.test(comboForDate.getValue())) {
				discardedFromHistory++;
				if (fineLog) {
					comboSums.add(comboForDate.getValue().stream().mapToInt(Integer::intValue).sum());
					System.out.println("  Filter discarded winning combo of " + CombinationFilterFactory.INSTANCE.simpleDateFormatter.format(comboForDate.getKey()) + ":  " +
						ComboHandler.toString(comboForDate.getValue()));
				}
			}
		}
		if (fineLog) {
			String comboExpression = ComboHandler.toExpression(comboSums);
			if (!comboExpression.isEmpty()) {
				System.out.println("\n" + ComboHandler.toExpression(comboSums));
			}
			System.out.println();
		}
		ComboHandler comboHandler = new ComboHandler(numbers, 6);
		Collection<Integer> comboPartitionIndexes = new HashSet<>();
		int discardedFromIntegralSystem = 0;
		int elaborationUnitSize = 25_000_000;
		combinationFilter = CombinationFilterFactory.INSTANCE.parse(filterAsString);
		for (int i = 0 ; i < comboHandler.getSizeAsInt(); i++) {
			comboPartitionIndexes.add(i);
			if (comboPartitionIndexes.size() == elaborationUnitSize) {
				/*if (fineLog) {
					System.out.println("Loaded " + integerFormat.format(i + 1) + " of indexes");
				}*/
				for (List<Integer> combo : comboHandler.find(comboPartitionIndexes, true).values()) {
					if (!combinationFilter.test(combo)) {
						discardedFromIntegralSystem++;
					}
				}
				if (fineLog) {
					System.out.println("Processed " + integerFormat.format(i + 1) + " of combos");
				}
			}
		}
		if (comboPartitionIndexes.size() > 0) {
			for (List<Integer> combo : comboHandler.find(comboPartitionIndexes, true).values()) {
				if (!combinationFilter.test(combo)) {
					discardedFromIntegralSystem++;
				}
			}
			if (fineLog && comboHandler.getSizeAsInt() >= elaborationUnitSize) {
				System.out.println("Processed " + integerFormat.format(comboHandler.getSizeAsInt()) + " of combo");
			}
		}
		if (fineLog && discardedFromHistory > 0) {
			System.out.println();
		}
		Map<String, Object> stats = new LinkedHashMap<>();
		double discardedPercentageFromHistory = (discardedFromHistory * 100) / (double)allWinningCombos.size();
		double maintainedPercentageFromHistory = 100d - discardedPercentageFromHistory;
		double discardedFromIntegralSystemPercentage = (discardedFromIntegralSystem * 100) / (double)comboHandler.getSizeAsInt();
		Double discardedFromHistoryEstimation =
			allWinningCombos.size() > 0 ?
				new BigDecimal(comboHandler.getSizeAsInt()).multiply(new BigDecimal(discardedFromHistory))
					.divide(new BigDecimal(allWinningCombos.size()), 2, RoundingMode.HALF_UP).doubleValue():
				Double.POSITIVE_INFINITY;
		Double maintainedFromHistoryEstimation =
			allWinningCombos.size() > 0 ?
				new BigDecimal(comboHandler.getSizeAsInt()).multiply(new BigDecimal(allWinningCombos.size() - discardedFromHistory))
					.divide(new BigDecimal(allWinningCombos.size()), 2, RoundingMode.HALF_DOWN).intValue():
				Double.POSITIVE_INFINITY;
		double effectiveness = (maintainedPercentageFromHistory + discardedFromIntegralSystemPercentage) / 2d;
		/*double effectiveness = ((discardedFromIntegralSystem - discardedFromHistoryEstimation) * 100d) /
				comboHandler.getSize();*/
		StringBuffer report = new StringBuffer();
		report.append("Total extractions analyzed:" + rightAlignedString(integerFormat.format(allWinningCombos.size()), 25) + "\n");
		report.append("Discarded winning combos:" + rightAlignedString(integerFormat.format(discardedFromHistory), 27) + "\n");
		report.append("Discarded winning combos percentage:" + rightAlignedString(decimalFormat.format(discardedPercentageFromHistory) + " %", 18) + "\n");
		report.append("Maintained winning combos percentage:" + rightAlignedString(decimalFormat.format(maintainedPercentageFromHistory) + " %", 17) + "\n");
		report.append("Estimated maintained winning combos:" + rightAlignedString(decimalFormat.format(maintainedFromHistoryEstimation), 16) + "\n");
		report.append("Integral system total combos:" + rightAlignedString(decimalFormat.format(comboHandler.getSizeAsInt()), 23) + "\n");
		report.append("Integral system discarded combos:" + rightAlignedString(decimalFormat.format(discardedFromIntegralSystem), 19) + "\n");
		report.append("Integral system discarded combos percentage:" + rightAlignedString(decimalFormat.format(discardedFromIntegralSystemPercentage) + " %", 10) + "\n");
		report.append("Effectiveness:" + rightAlignedString(decimalFormat.format(effectiveness) + " %", 40) +"\n");
		System.out.println(report.toString() + "\nFilter analysis ended\n");

		stats.put("totalExtractionsAnalyzed", allWinningCombos.size());
		stats.put("discardedWinningCombos", discardedFromHistory);
		stats.put("discardedWinningCombosPercentage", discardedPercentageFromHistory);
		stats.put("maintainedWinningCombosPercentage", maintainedPercentageFromHistory);
		stats.put("estimatedMaintainedWinningCombos", maintainedFromHistoryEstimation);
		stats.put("integralSystemTotalCombos", comboHandler.getSizeAsInt());
		stats.put("integralSystemDiscardedCombos", discardedFromIntegralSystem);
		stats.put("integralSystemDiscardedCombosPercentage", discardedFromIntegralSystemPercentage);
		stats.put("report", report);
		return stats;
	}

	private String rightAlignedString(String value, int emptySpacesCount) {
		return String.format("%" + emptySpacesCount + "s", value);
	}

	@Override
	protected String processStatsExpression(String expression) {
		String[] options = expression.replaceAll("\\s+","").split("lessExtCouple|lessExt|mostExtCouple|mostExt");
		List<Integer> numbersToBeTested = null;
		if (expression.contains("lessExtCouple")) {
			numbersToBeTested =
				getSEStats().getExtractedNumberFromMostExtractedCoupleRankReversed();
		} else if (expression.contains("lessExt")) {
			numbersToBeTested =
				getSEStats().getExtractedNumberRankReversed();
		} else if (expression.contains("mostExtCouple")) {
			numbersToBeTested =
				getSEStats().getExtractedNumberFromMostExtractedCoupleRank();
		} else if (expression.contains("mostExt")) {
			numbersToBeTested =
				getSEStats().getExtractedNumberRank();
		}
		String[] subRange = options[0].split("->");
		if (subRange.length == 2) {
			Integer leftBound = Integer.parseInt(subRange[0]);
			Integer rightBound = Integer.parseInt(subRange[1]);
			numbersToBeTested = numbersToBeTested.stream().filter(number -> number >= leftBound && number <= rightBound).collect(Collectors.toList());
		}
		String[] groupOptions = options[1].split(":");
		List<String> numbers = new ArrayList<>();
		if (groupOptions[0].contains("->")) {
			String[] bounds = groupOptions[0].split("->");
			for (int i = Integer.parseInt(bounds[0]); i <= Integer.parseInt(bounds[1]); i++) {
				numbers.add(numbersToBeTested.get(i - 1).toString());
			}
		} else if (groupOptions[0].contains(",")) {
			for (String index : groupOptions[0].split(",")) {
				numbers.add(numbersToBeTested.get(Integer.parseInt(index) - 1).toString());
			}
		}
		return "in " + String.join(",", numbers) + ": " + groupOptions[1];
	}

	@Override
	protected String processInExpression(String expression) {
		String[] options = expression.replaceAll("\\s+","").split("inallWinningCombos");
		if (options.length > 1) {
			String[] groupOptions = options[1].split(":");
			List<String> inClauses = new ArrayList<>();
			for (List<Integer> winningCombo :getSEStats().getAllWinningCombos().values()) {
				inClauses.add("in " + ComboHandler.toString(winningCombo, ",") + ":" + groupOptions[1]);
			}
			return "(" + String.join("|", inClauses) + ")";
		}
		return expression;
	}

	@Override
	protected String processMathExpression(String expression) {
		String[] options = expression.split("allWinningCombos");
		if (options.length > 1) {
			options[1] = options[1].startsWith(".") ?
					options[1].replaceFirst("\\.", "") : options[1];
			String manipulatedExpression = null;
			if ((manipulatedExpression = processMathManipulationExpression(
				options[1], "sum", "rangeOfSum",
				operationOptionValue -> {
					Collection<Integer> sums = new TreeSet<>();
					getSEStats().getAllWinningCombos().values().stream().forEach(combo ->
						sums.add(ComboHandler.sumValues(combo))
					);
					return sums;
				}
			)) != null) {
				return manipulatedExpression;
			} else if ((manipulatedExpression = processMathManipulationExpression(
				options[1], "sumOfPower", "rangeOfSumPower",
				operationOptionValue -> {
					Collection<Integer> sums = new TreeSet<>();
					getSEStats().getAllWinningCombos().values().stream().forEach(combo -> {
						sums.add(ComboHandler.sumPowerOfValues(combo, operationOptionValue.get(0)));
					});
					return sums;
				}
			)) != null) {
				return manipulatedExpression;
			}
			throw new UnsupportedOperationException("Expression is not supported: " + expression);
		}
		return expression;
	}

	protected SEStats getSEStats() {
		SEStats sEStats = SEStats.get(getExtractionArchiveStartDate(), simpleDateFormatter.format(extractionDate));
		if (LocalDate.now().compareTo(extractionDate) >= 0) {
			Date latestExtractionDate = sEStats.getLatestExtractionDate();
			if (latestExtractionDate != null && latestExtractionDate.toInstant()
				.atZone(ZoneId.of(SEStats.DEFAULT_TIME_ZONE))
			    .toLocalDate().compareTo(extractionDate) == 0
			) {
				latestExtractionDate = sEStats.getLatestExtractionDate(2);
				if (latestExtractionDate != null) {
					sEStats = SEStats.get(getExtractionArchiveStartDate(), sEStats.defaultDateFmt.format(latestExtractionDate));
				} else {
					sEStats = SEStats.get(
						getExtractionArchiveStartDate(),
						LocalDate.parse(
							getExtractionArchiveStartDate(), simpleDateFormatter
						).minus(1, ChronoUnit.DAYS).format(simpleDateFormatter)
					);
				}
			}
		}
		return sEStats;
	}

	private String processMathManipulationExpression(
		String expressionNameWithOptions,
		String expressionNameToMatch,
		String rangedExpressionNameToMatch,
		Function<List<Integer>, Collection<Integer>> processor
	) {
		Pattern comboPowerExpPattern = Pattern.compile("(\\b" + expressionNameToMatch +"\\b|\\b" + rangedExpressionNameToMatch + "\\b)");
		Matcher comboPowerExpFinder = comboPowerExpPattern.matcher(expressionNameWithOptions);
		if (comboPowerExpFinder.find()) {
			String mathExpressionType = comboPowerExpFinder.group(1);
			String[] operationOptions = expressionNameWithOptions.split(comboPowerExpPattern.pattern());
			List<Integer> operationOptionValues = new ArrayList<>();
			if (operationOptions.length > 1) {
				operationOptionValues.addAll(Arrays.stream(operationOptions[1].replaceAll("\\s+","").split(",")).map(Integer::parseInt).collect(Collectors.toList()));
			}
			Collection<Integer> sums = processor.apply(operationOptionValues);
			if (sums.isEmpty()) {
				return "true";
			}
			if (mathExpressionType.contains(expressionNameToMatch)) {
				return expressionNameToMatch + ComboHandler.toString(operationOptionValues, ",") + ": " + String.join(",", sums.stream().map(Object::toString).collect(Collectors.toList()));
			} else if (mathExpressionType.contains(rangedExpressionNameToMatch)) {
				return expressionNameToMatch + ComboHandler.toString(
					operationOptionValues, ","
				) + ": " + sums.iterator().next() + " -> " + sums.stream().reduce((prev, next) -> next).orElse(null);
			}
		}
		return null;
	}

	@Override
	protected String getDefaultNumberRange() {
		return "1 -> 90";
	}

	@Override
	protected Map<String, Object> checkQuality(Storage storage) {
		return getSEStats()
			.checkQuality(storage::iterator);
	}

}