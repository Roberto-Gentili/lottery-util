package org.rg.game.lottery.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import java.util.function.Function;
import java.util.function.Predicate;

public class SELotteryMatrixGeneratorEngine extends LotteryMatrixGeneratorAbstEngine {
	private static final List<Map<String,List<Integer>>> allChosenNumbers;
	private static final List<Map<String,List<Integer>>> allDiscardedNumbers;

	static {
		allChosenNumbers = new ArrayList<>();
		allDiscardedNumbers = new ArrayList<>();
	}

	public SELotteryMatrixGeneratorEngine() {
		super();
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
		long seed = 3539L;
		LocalDate seedStartDate = LocalDate.parse("2023-02-11");
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

	@Override
	protected List<Map<String,List<Integer>>> getAllChosenNumbers() {
		return allChosenNumbers;
	}

	@Override
	protected List<Map<String,List<Integer>>> getAllDiscardedNumbers() {
		return allDiscardedNumbers;
	}

	@Override
	protected Function<Integer, Function<Integer, Function<Integer, Iterator<Integer>>>> getNumberGeneratorFactory() {
		return generatorType-> leftBound -> rightBound -> {
			if (generatorType == 3) {
				return random.ints(leftBound , rightBound + 1).iterator();
			} else if (generatorType == 1) {
				return new BoundedIterator(SEExtractionArchive.get(getExtractionArchiveStartDate()).getExtractedNumberRank(), leftBound, rightBound);
			} else if (generatorType == 2) {
				return new BoundedIterator(SEExtractionArchive.get(getExtractionArchiveStartDate()).getExtractedNumberFromMostExtractedCoupleRank(), leftBound, rightBound);
			}
			throw new IllegalArgumentException("Unvalid generator type");
		};
	}

	@Override
	protected String getDefaultExtractionArchiveStartDate() {
		return "01/07/2009";
	}

	@Override
	public void testEffectiveness(String filterAsString, List<Integer> numbers, boolean fineLog) {
		Predicate<List<Integer>> combinationFilter = CombinationFilterFactory.INSTANCE.parse(filterAsString);
		Set<Entry<Date, List<Integer>>> allWinningCombos = SEExtractionArchive.get(getExtractionArchiveStartDate()).getAllWinningCombos().entrySet();
		int discardedFromHistory = 0;
		System.out.println("Starting filter analysis\n");
		for (Map.Entry<Date, List<Integer>> comboForDate : allWinningCombos) {
			if (!combinationFilter.test(comboForDate.getValue())) {
				discardedFromHistory++;
				if (fineLog) {
					System.out.println("Filter discarded winning combo of " + CombinationFilterFactory.INSTANCE.simpleDateFormatter.format(comboForDate.getKey()) + ":  " +
						CombinationFilterFactory.INSTANCE.toString(comboForDate.getValue()));
				}
			}
		}
		if (fineLog) {
			System.out.println();
		}
		ComboHandler comboHandler = new ComboHandler(numbers, 6);
		Collection<Integer> comboPartitionIndexes = new HashSet<>();
		int discardedFromIntegralSystem = 0;
		int elaborationUnitSize = 25_000_000;
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

		double discardedPercentageFromHistory = (discardedFromHistory * 100) / (double)allWinningCombos.size();
		double maintainedPercentageFromHistory = 100d - discardedPercentageFromHistory;
		double discardedFromIntegralSystemPercentage = (discardedFromIntegralSystem * 100) / (double)comboHandler.getSize();
		double effectiveness = (maintainedPercentageFromHistory + discardedFromIntegralSystemPercentage) / 2d;
		System.out.println("Total extractions analyzed:" + rightAlignedString(integerFormat.format(allWinningCombos.size()), 25));
		System.out.println("Discarded winning combos:" + rightAlignedString(integerFormat.format(discardedFromHistory), 27));
		System.out.println("Discarded winning combos percentage:" + rightAlignedString(decimalFormat.format(discardedPercentageFromHistory) + " %", 18));
		System.out.println("Maintained winning combos percentage:" + rightAlignedString(decimalFormat.format(maintainedPercentageFromHistory) + " %", 17));
		System.out.println("Integral system total combos:" + rightAlignedString(decimalFormat.format(comboHandler.getSize()), 23));
		System.out.println("Integral system discarded combos:" + rightAlignedString(decimalFormat.format(discardedFromIntegralSystem), 19));
		System.out.println("Integral system discarded combos percentage:" + rightAlignedString(decimalFormat.format(discardedFromIntegralSystemPercentage) + " %", 10));
		System.out.println("Effectiveness:" + rightAlignedString(decimalFormat.format(effectiveness) + " %", 40) +"\n\n");
	}

	private String rightAlignedString(String value, int emptySpacesCount) {
		return String.format("%" + emptySpacesCount + "s", value);
	}

}