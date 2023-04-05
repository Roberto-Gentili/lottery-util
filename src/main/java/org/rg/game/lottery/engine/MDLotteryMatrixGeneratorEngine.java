package org.rg.game.lottery.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

public class MDLotteryMatrixGeneratorEngine extends LotteryMatrixGeneratorAbstEngine {
	private static final List<Map<String,List<Integer>>> allChosenNumbers;
	private static final List<Map<String,List<Integer>>> allExcluededNumbers;

	static {
		allChosenNumbers = new ArrayList<>();
		allExcluededNumbers = new ArrayList<>();
	}

	public MDLotteryMatrixGeneratorEngine() {
		super();
	}

	@Override
	protected LocalDate computeNextExtractionDate(LocalDate startDate, boolean incrementIfExpired) {
		if (incrementIfExpired) {
			while (LocalDateTime.now(ZoneId.of("Europe/Rome")).compareTo(
				LocalDateTime.now(ZoneId.of("Europe/Rome")).with(startDate).withHour(18).withMinute(45).withSecond(0).withNano(0)
			) > 0) {
				startDate = startDate.plus(1, ChronoUnit.DAYS);
			}
		}
		return startDate;
	}

	@Override
	protected int getIncrementDays(LocalDate startDate) {
		return 1;
	}

	@Override
	protected List<Map<String,List<Integer>>> getAllChosenNumbers() {
		return allChosenNumbers;
	}

	@Override
	protected List<Map<String,List<Integer>>> getAllDiscardedNumbers() {
		return allExcluededNumbers;
	}

	@Override
	protected List<LocalDate> forWeekOf(LocalDate dayOfWeek) {
		List<LocalDate> dates = new ArrayList<>();
		LocalDate nextWeekStart = dayOfWeek.with(DayOfWeek.MONDAY);
		dates.add(nextWeekStart);
		for (int i = 0; i < 6; i++) {
			dates.add(nextWeekStart = nextWeekStart.plus(getIncrementDays(nextWeekStart), ChronoUnit.DAYS));
		}
		return dates;
	}

	@Override
	protected Map<String, Object> adjustSeed(LocalDate extractionDate) {
		long seed = 1L;
		LocalDate seedStartDate = LocalDate.parse("2018-02-17");
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
	protected Function<Integer, Function<Integer, Function<Integer, Iterator<Integer>>>> getNumberGeneratorFactory() {
		return generatorType-> leftBound -> rightBound -> {
			if (generatorType == 3) {
				return random.ints(leftBound , rightBound + 1).iterator();
			}
			throw new IllegalArgumentException("Unvalid generator type");
		};
	}

	@Override
	protected void testEffectiveness(String combinationFilterRaw, List<Integer> numbers, boolean parseBoolean) {
		throw new UnsupportedOperationException("Effectiveness test");

	}

	@Override
	protected String getDefaultExtractionArchiveStartDate() {
		return null;
	}
}