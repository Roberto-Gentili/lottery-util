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
	protected LocalDate computeNextExtractionDate(LocalDate startDate, boolean incrementIfExpired) {
		if (incrementIfExpired) {
			while (LocalDateTime.now(ZoneId.of("Europe/Rome")).compareTo(
				LocalDateTime.now(ZoneId.of("Europe/Rome")).with(startDate).withHour(19).withMinute(0).withSecond(0).withNano(0)
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
	protected Map<String, Object> adjustSeed(LocalDate extractionDate) {
		Map<String, Object> competiotionInfo = getCompetitionInfo();
		long seed = (long)competiotionInfo.get("startSeed");
		LocalDate seedStartDate = (LocalDate)competiotionInfo.get("startDate");
		if (seedStartDate.compareTo(extractionDate) >= 0) {
			throw new IllegalArgumentException("Unvalid date: " + extractionDate);
		}
		while (seedStartDate.compareTo(extractionDate) < 0) {
			seedStartDate = seedStartDate.plus(getIncrementDays(seedStartDate), ChronoUnit.DAYS);
			seed++;
		}
		random = new Random(seed);
		Map<String, Object> seedData = new LinkedHashMap<>();
		seedData.put("seed", seed);
		seedData.put("seedStartDate", seedStartDate);
		return seedData;
	}

	private Map<String, Object> getCompetitionInfo() {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("startSeed", 3540L);
		data.put("startDate", LocalDate.parse("14/02/2023", simpleDateFormatter));
		/*data.put("startSeed", 1L);
		data.put("startDate", LocalDate.parse(getDefaultExtractionArchiveStartDate(), simpleDateFormatter));*/
		return data;
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
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberRank(), leftBound, rightBound);
			} else if (NumberProcessor.MOST_EXTRACTED_COUPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedCoupleRank(), leftBound, rightBound);
			} else if (NumberProcessor.MOST_EXTRACTED_TRIPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedTripleRank(), leftBound, rightBound);
			} else if (NumberProcessor.LESS_EXTRACTED_KEY.equals(generatorType)) {
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberRankReversed(), leftBound, rightBound);
			} else if (NumberProcessor.LESS_EXTRACTED_COUPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedCoupleRankReversed(), leftBound, rightBound);
			} else if (NumberProcessor.LESS_EXTRACTED_TRIPLE_KEY.equals(generatorType)) {
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedTripleRankReversed(), leftBound, rightBound);
			} else if (NumberProcessor.NEAREST_FROM_RECORD_ABSENCE_PERCENTAGE_KEY.equals(generatorType)) {
				return new BoundedIterator(SEStats.get(getExtractionArchiveStartDate()).getDistanceFromAbsenceRecordPercentageRankReversed(), leftBound, rightBound);
			}
			throw new IllegalArgumentException("Unvalid generator type");
		};
	}

	@Override
	protected String getDefaultExtractionArchiveStartDate() {
		return "02/07/2009";
	}

	@Override
	public Map<String, Number> testEffectiveness(String filterAsString, List<Integer> numbers, boolean fineLog) {
		filterAsString = preProcess(filterAsString);
		Predicate<List<Integer>> combinationFilter = CombinationFilterFactory.INSTANCE.parse(filterAsString, fineLog);
		Set<Entry<Date, List<Integer>>> allWinningCombos = SEStats.get(getExtractionArchiveStartDate()).getAllWinningCombos().entrySet();
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
		for (int i = 0 ; i < comboHandler.getSize(); i++) {
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
			if (fineLog && comboHandler.getSize() >= elaborationUnitSize) {
				System.out.println("Processed " + integerFormat.format(comboHandler.getSize()) + " of combo");
			}
		}
		if (fineLog && discardedFromHistory > 0) {
			System.out.println();
		}
		Map<String, Number> stats = new LinkedHashMap<>();
		double discardedPercentageFromHistory = (discardedFromHistory * 100) / (double)allWinningCombos.size();
		double maintainedPercentageFromHistory = 100d - discardedPercentageFromHistory;
		double discardedFromIntegralSystemPercentage = (discardedFromIntegralSystem * 100) / (double)comboHandler.getSize();
		double discardedFromHistoryEstimation = new BigDecimal(comboHandler.getSize()).multiply(new BigDecimal(discardedFromHistory))
				.divide(new BigDecimal(allWinningCombos.size()), 2, RoundingMode.HALF_UP).doubleValue();
		int maintainedFromHistoryEstimation = new BigDecimal(comboHandler.getSize()).multiply(new BigDecimal(allWinningCombos.size() - discardedFromHistory))
				.divide(new BigDecimal(allWinningCombos.size()), 2, RoundingMode.HALF_DOWN).intValue();
		double effectiveness = (maintainedPercentageFromHistory + discardedFromIntegralSystemPercentage) / 2d;
		/*double effectiveness = ((discardedFromIntegralSystem - discardedFromHistoryEstimation) * 100d) /
				comboHandler.getSize();*/
		System.out.println("Total extractions analyzed:" + rightAlignedString(integerFormat.format(allWinningCombos.size()), 25));
		System.out.println("Discarded winning combos:" + rightAlignedString(integerFormat.format(discardedFromHistory), 27));
		System.out.println("Discarded winning combos percentage:" + rightAlignedString(decimalFormat.format(discardedPercentageFromHistory) + " %", 18));
		System.out.println("Maintained winning combos percentage:" + rightAlignedString(decimalFormat.format(maintainedPercentageFromHistory) + " %", 17));
		System.out.println("Estimated maintained winning combos:" + rightAlignedString(decimalFormat.format(maintainedFromHistoryEstimation), 16));
		System.out.println("Integral system total combos:" + rightAlignedString(decimalFormat.format(comboHandler.getSize()), 23));
		System.out.println("Integral system discarded combos:" + rightAlignedString(decimalFormat.format(discardedFromIntegralSystem), 19));
		System.out.println("Integral system discarded combos percentage:" + rightAlignedString(decimalFormat.format(discardedFromIntegralSystemPercentage) + " %", 10));
		System.out.println("Effectiveness:" + rightAlignedString(decimalFormat.format(effectiveness) + " %", 40) +"\n\n");
		stats.put("totalExtractionsAnalyzed", allWinningCombos.size());
		stats.put("discardedWinningCombos", discardedFromHistory);
		stats.put("discardedWinningCombosPercentage", discardedPercentageFromHistory);
		stats.put("maintainedWinningCombosPercentage", maintainedPercentageFromHistory);
		stats.put("estimatedMaintainedWinningCombos", maintainedFromHistoryEstimation);
		stats.put("integralSystemTotalCombos", comboHandler.getSize());
		stats.put("integralSystemDiscardedCombos", discardedFromIntegralSystem);
		stats.put("integralSystemDiscardedCombosPercentage", discardedFromIntegralSystemPercentage);
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
				SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedCoupleRankReversed();
		} else if (expression.contains("lessExt")) {
			numbersToBeTested =
				SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberRankReversed();
		} else if (expression.contains("mostExtCouple")) {
			numbersToBeTested =
				SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedCoupleRank();
		} else if (expression.contains("mostExt")) {
			numbersToBeTested =
				SEStats.get(getExtractionArchiveStartDate()).getExtractedNumberRank();
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
					SEStats.get(getExtractionArchiveStartDate()).getAllWinningCombos().values().stream().forEach(combo ->
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
					SEStats.get(getExtractionArchiveStartDate()).getAllWinningCombos().values().stream().forEach(combo -> {
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
			if (mathExpressionType.contains(expressionNameToMatch)) {
				if (!sums.isEmpty()) {
					return expressionNameToMatch + ComboHandler.toString(operationOptionValues, ",") + ": " + String.join(",", sums.stream().map(Object::toString).collect(Collectors.toList()));
				}
				return "true";
			} else if (mathExpressionType.contains(rangedExpressionNameToMatch)) {
				return expressionNameToMatch + ComboHandler.toString(operationOptionValues, ",") + ": " + sums.iterator().next() + " -> " + sums.stream().reduce((prev, next) -> next).orElse(null);
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
		return SEStats.get(getExtractionArchiveStartDate())
			.checkQuality(storage::iterator);
	}

}