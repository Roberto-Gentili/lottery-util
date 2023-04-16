package org.rg.game.lottery.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class LotteryMatrixGeneratorAbstEngine {
	private static final NumberProcessor numberProcessor;

	static {
		numberProcessor = new NumberProcessor();
	}

	public Random random;
	protected boolean reportEnabled;
	protected boolean reportDetailEnabled;
	protected Function<Integer, Integer> comboIndexSupplier;
	protected String comboIndexSelectorType;

	protected DecimalFormat decimalFormat = new DecimalFormat( "#,##0.##" );
	protected DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	protected DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	protected DateTimeFormatter simpleDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	protected String extractionArchiveStartDate;
	protected String storageType;
	protected Runnable executor;
	protected int engineIndex;
	protected Integer avoidMode;
	protected Predicate<List<Integer>> combinationFilter;
	protected ExpressionToPredicateEngine<List<Integer>> combinationFilterPreProcessor;

	LotteryMatrixGeneratorAbstEngine() {
		engineIndex = getAllChosenNumbers().size();
		combinationFilterPreProcessor = new ExpressionToPredicateEngine<>();
		setupCombinationFilterPreProcessor();
	}

	public void setup(Properties config) {
		Collection<LocalDate> extractionDates = new LinkedHashSet<>();
		extractionArchiveStartDate = config.getProperty("competition.archive.start-date");
		comboIndexSelectorType = config.getProperty("combination.selector", "random");
		String extractionDatesAsString = config.getProperty("competition");
		if (extractionDatesAsString == null || extractionDatesAsString.isEmpty()) {
			extractionDates.add(LocalDate.now());
		} else {
			DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			for(String date : extractionDatesAsString.replaceAll("\\s+","").split(",")) {
				String[] dateWithOffset = date.split("\\+");
				if ("thisWeek".equalsIgnoreCase(dateWithOffset[0])) {
					if (dateWithOffset.length == 2) {
						String[] range = dateWithOffset[1].split("\\*");
						if (range.length == 2) {
							for (int i = 0; i < Integer.parseInt(range[1]); i++) {
								extractionDates.addAll(forNextWeek(Integer.parseInt(range[0])+i));
							}
						} else {
							extractionDates.addAll(forNextWeek(Integer.valueOf(range[0])));
						}
					} else {
						extractionDates.addAll(forThisWeek());
					}
				} else {
					LocalDate extractionDate = "next".equalsIgnoreCase(dateWithOffset[0])?
						computeNextExtractionDate(LocalDate.now(), true) :
						computeNextExtractionDate(LocalDate.parse(dateWithOffset[0], datePattern), false);
					if (dateWithOffset.length == 2) {
						String[] range = dateWithOffset[1].split("\\*");
						for (int i = 0; i < Integer.parseInt(range[0]); i++) {
							extractionDate = extractionDate.plus(getIncrementDays(extractionDate), ChronoUnit.DAYS);
						}
						if (range.length == 2) {
							for (int i = 0; i < Integer.parseInt(range[1]); i++) {
								extractionDates.add(extractionDate);
								extractionDate = extractionDate.plus(getIncrementDays(extractionDate), ChronoUnit.DAYS);
							}
						} else {
							extractionDates.add(extractionDate);
						}
					} else {
						extractionDates.add(extractionDate);
					}
				}
			}
		}
		storageType = config.getProperty("storage", "memory").replaceAll("\\s+","");
		String combinationFilterRaw = config.getProperty("combination.filter");
		combinationFilter = CombinationFilterFactory.INSTANCE.parse(
			preProcess(combinationFilterRaw)
		);
		Function<LocalDate, Map<String, Object>> basicDataSupplier = extractionDate -> {
			Map<String, Object> data = adjustSeed(extractionDate);
			String numbersOrdered = config.getProperty("numbers.ordered");
			NumberProcessor.Context numberProcessorContext = new NumberProcessor.Context(
				getNumberGeneratorFactory(), engineIndex, getAllChosenNumbers(), getAllDiscardedNumbers()
			);
			List<Integer> chosenNumbers = numberProcessor.retrieveNumbersToBePlayed(
				numberProcessorContext,
				config.getProperty("numbers", getDefaultNumberRange()),
				extractionDate,
				numbersOrdered != null && Boolean.parseBoolean(numbersOrdered)
			);
			data.put("chosenNumbers", chosenNumbers);
			List<Integer> numbersToBePlayed = new ArrayList<>(chosenNumbers);
			data.put(
				"numbersToBeDiscarded",
				numberProcessor.retrieveNumbersToBeExcluded(
					numberProcessorContext,
					config.getProperty("numbers.discard"),
					extractionDate,
					numbersToBePlayed,
					numbersOrdered != null && Boolean.parseBoolean(numbersOrdered)
				)
			);
			data.put("numbersToBePlayed", numbersToBePlayed);
			return data;
		};
		for (LocalDate extractionDate : extractionDates) {
			Map<String,List<Integer>> numbersToBePlayedForData = null;
			Map<String,List<Integer>> numbersToBeExludedForData = null;
			try {
				numbersToBePlayedForData = getAllChosenNumbers().get(engineIndex);
				numbersToBeExludedForData = getAllDiscardedNumbers().get(engineIndex);
			} catch (IndexOutOfBoundsException exc) {
				getAllChosenNumbers().add(numbersToBePlayedForData = new LinkedHashMap<>());
				getAllDiscardedNumbers().add(numbersToBeExludedForData = new LinkedHashMap<>());
			}
			Map<String, Object> basicData = basicDataSupplier.apply(extractionDate);
			numbersToBePlayedForData.put(
				simpleDateFormatter.format(extractionDate),
				(List<Integer>)basicData.get("chosenNumbers")
			);
			numbersToBeExludedForData.put(
				simpleDateFormatter.format(extractionDate),
				(List<Integer>)basicData.get("numbersToBeDiscarded")
			);
		}
		reportEnabled = Boolean.parseBoolean(config.getProperty("report.enabled", "true"));
		reportDetailEnabled = Boolean.parseBoolean(config.getProperty("report.detail.enabled", "false"));
		executor = () -> {
			for (LocalDate extractionDate : extractionDates) {
				generate(
					basicDataSupplier,
					combinationFilterRaw,
					Boolean.parseBoolean(config.getProperty("combination.filter.test", "true")),
					Boolean.parseBoolean(config.getProperty("combination.filter.test.fine-info", "true")),
					Integer.valueOf(config.getProperty("combination.components")),
					Optional.ofNullable(config.getProperty("numbers.occurrences")).map(Double::parseDouble).orElseGet(() -> null),
					Optional.ofNullable(config.getProperty("combination.count")).map(Integer::parseInt).orElseGet(() -> null),
					Integer.valueOf(config.getProperty("combination.choose-random.count", "0")),
					() -> {
						String equilibrateCombinations = config.getProperty("combination.equilibrate");
						if (equilibrateCombinations != null && !equilibrateCombinations.isEmpty() && !equilibrateCombinations.replaceAll("\\s+","").equalsIgnoreCase("random")) {
							return Boolean.parseBoolean(equilibrateCombinations);
						}
						return random.nextBoolean();
					},
					extractionDate,
					Boolean.parseBoolean(config.getProperty("combination.magic.enabled", "true")) ?
						Integer.valueOf(Optional.ofNullable(config.getProperty("combination.magic.min-number")).orElseGet(() -> "1"))
						:null,
					Boolean.parseBoolean(config.getProperty("combination.magic.enabled", "true")) ?
						Integer.valueOf(Optional.ofNullable(config.getProperty("combination.magic.max-number")).orElseGet(() -> "90"))
						:null,
					config.getProperty("nameSuffix")
				);
				System.out.println("\n\n");
			}
		};
		String avoidModeConfigValue = config.getProperty("avoid", "never");
		if (avoidModeConfigValue.equals("never")) {
			avoidMode = 0;
		} else if (avoidModeConfigValue.equals("if not suggested")) {
			avoidMode = 1;
		} else if (avoidModeConfigValue.equals("if not strongly suggested")) {
			avoidMode = 2;
		}
	}

	protected abstract String getDefaultNumberRange();

	public String preProcess(String filterAsString) {
		return combinationFilterPreProcessor.preProcess(filterAsString);
	}

	protected void setupCombinationFilterPreProcessor() {
		combinationFilterPreProcessor.addSimpleExpressionPreprocessor(
			expression ->
				expression.split("lessExtCouple|lessExt|mostExtCouple|mostExt").length > 1,
			expression ->
				parameters ->
					processStatsExpression(expression)
		);
		combinationFilterPreProcessor.addSimpleExpressionPreprocessor(
			expression ->
				expression.contains("sum"),
			expression ->
				parameters ->
					processMathExpression(expression)
		);
		combinationFilterPreProcessor.addSimpleExpressionPreprocessor(
			expression ->
				expression.contains("in"),
			expression ->
				parameters ->
					processInExpression(expression)
		);
	}

	protected String processInExpression(String expression) {
		throw new UnsupportedOperationException("Expression is not supported: " + expression);
	}

	protected String processMathExpression(String expression) {
		throw new UnsupportedOperationException("Expression is not supported: " + expression);
	}

	protected String processStatsExpression(String expression) {
		throw new UnsupportedOperationException("Expression is not supported: " + expression);
	}

	protected abstract Map<String, Number> testEffectiveness(String combinationFilterRaw, List<Integer> numbers, boolean parseBoolean);

	private List<LocalDate> forThisWeek() {
		return forWeekOf(LocalDate.now());
	}

	private List<LocalDate> forNextWeek() {
		return forNextWeek(1);
	}

	private List<LocalDate> forNextWeek(int offset) {
		return forWeekOf(LocalDate.now().plus(offset, ChronoUnit.WEEKS));
	}

	private Storage buildStorage(
		LocalDate extractionDate,
		int combinationCount,
		int numberOfCombos,
		List<Integer> numbers,
		String suffix
	) {
		if ("memory".equalsIgnoreCase(storageType)) {
			return new MemoryStorage(numbers);
		}
		return new PersistentStorage(extractionDate, combinationCount, numberOfCombos, numbers, suffix);
	}

	public void generate(
		Function<LocalDate, Map<String, Object>> basicDataSupplier,
		String combinationFilterRaw,
		boolean testFilter,
		boolean testFilterFineInfo,
		int combinationComponents,
		Double occurrencesNumberRequested,
		Integer numberOfCombosRequested,
		int chooseRandom,
		BooleanSupplier equilibrateFlagSupplier,
		LocalDate extractionDate,
		Integer magicCombinationMinNumber,
		Integer magicCombinationMaxNumber,
		String suffix
	) {
		Map<String, Object> data = basicDataSupplier.apply(extractionDate);
		List<Integer> numbers = (List<Integer>)data.get("numbersToBePlayed");
		if (combinationFilterRaw != null && testFilter) {
			testEffectiveness(combinationFilterRaw, numbers, testFilterFineInfo);
		}
		Double ratio;
		Double occurrencesNumber = occurrencesNumberRequested;
		Integer numberOfCombos = numberOfCombosRequested;
		if (numberOfCombos != null) {
			ratio = (combinationComponents * numberOfCombos) / (double)numbers.size();
		} else {
			ratio = occurrencesNumber;
			numberOfCombos = new BigDecimal((ratio * numbers.size()) / combinationComponents).setScale(0, RoundingMode.UP).intValue();
		}
		Storage storageRef = null;
		AtomicInteger effectiveRandomCounter = new AtomicInteger(0);
		AtomicLong discardedComboCounter = new AtomicLong(0);
		ComboHandler comboHandler = new ComboHandler(numbers, combinationComponents);
		try (Storage storage = buildStorage(((LocalDate)data.get("seedStartDate")), combinationComponents, numberOfCombos, numbers, suffix);) {
			storageRef = storage;
			boolean equilibrate = equilibrateFlagSupplier.getAsBoolean();
			AtomicReference<Iterator<List<Integer>>> randomCombosIteratorWrapper = new AtomicReference<>();
			boolean[] alreadyComputed = new boolean[comboHandler.getSizeAsInt()];
			AtomicLong indexGeneratorCallsCounter = new AtomicLong(0L);
			AtomicInteger uniqueIndexCounter = new AtomicInteger(0);
			Integer ratioAsInt = null;
			Integer remainder = null;
			try {
				if (equilibrate) {
					Map<Integer, AtomicInteger> occurrences = new LinkedHashMap<>();
					remainder = (combinationComponents * numberOfCombos) % numbers.size();
					ratioAsInt = ratio.intValue();
					while (storage.size() < numberOfCombos) {
						List<Integer> underRatioNumbers = new ArrayList<>(numbers);
						List<Integer> overRatioNumbers = new ArrayList<>();
						for (Entry<Integer, AtomicInteger> entry : occurrences.entrySet()) {
							if (entry.getValue().get() >= ratioAsInt) {
								underRatioNumbers.remove(entry.getKey());
								if (entry.getValue().get() > ratioAsInt) {
									overRatioNumbers.add(entry.getKey());
								}
							}
						}
						List<Integer> selectedCombo;
						if (underRatioNumbers.size() < combinationComponents) {
							do {
								selectedCombo = getNextCombo(
									randomCombosIteratorWrapper,
									comboHandler,
									alreadyComputed,
									indexGeneratorCallsCounter,
									uniqueIndexCounter,
									effectiveRandomCounter,
									discardedComboCounter
								);
							} while(selectedCombo == null || !selectedCombo.containsAll(underRatioNumbers) || containsOneOf(overRatioNumbers, selectedCombo));
							if (storage.addCombo(selectedCombo)) {
								incrementOccurences(occurrences, selectedCombo);
							}
						} else {
							do {
								selectedCombo = getNextCombo(
									randomCombosIteratorWrapper,
									comboHandler,
									alreadyComputed,
									indexGeneratorCallsCounter,
									uniqueIndexCounter,
									effectiveRandomCounter,
									discardedComboCounter
								);
							} while(selectedCombo == null);
							boolean canBeAdded = true;
							for (Integer number : selectedCombo) {
								AtomicInteger counter = occurrences.computeIfAbsent(number, key -> new AtomicInteger(0));
								if (counter.get() >= ratioAsInt) {
									canBeAdded = false;
									break;
								}
							}
							if (canBeAdded && storage.addCombo(selectedCombo)) {
								incrementOccurences(occurrences, selectedCombo);
							}
						}
					}
				} else {
					List<Integer> numbersCloned = new ArrayList<>(numbers);
					List<Integer> selectedCombo;
					for (int i = 0; i < numberOfCombos; i++) {
						do {
							selectedCombo = getNextCombo(
								randomCombosIteratorWrapper,
								comboHandler,
								alreadyComputed,
								indexGeneratorCallsCounter,
								uniqueIndexCounter,
								effectiveRandomCounter,
								discardedComboCounter
							);
						} while(selectedCombo == null);
						effectiveRandomCounter.incrementAndGet();
						if (storage.addCombo(selectedCombo)) {
							numbersCloned.removeAll(
								selectedCombo
						    );
						}
					}
					while (!numbersCloned.isEmpty()) {
						do {
							selectedCombo = getNextCombo(
								randomCombosIteratorWrapper,
								comboHandler,
								alreadyComputed,
								indexGeneratorCallsCounter,
								uniqueIndexCounter,
								effectiveRandomCounter,
								discardedComboCounter
							);
						} while(selectedCombo == null);
						effectiveRandomCounter.incrementAndGet();
						if (storage.addCombo(selectedCombo)) {
							numbersCloned.removeAll(
								selectedCombo
						    );
						}
					}
				}
			} catch (AllRandomNumbersHaveBeenGeneratedException exc) {
				System.out.println("\n" + exc.getMessage());
			}
			System.out.println(
				"\n\n" +
				"Per il concorso numero " + data.get("seed") + " del " + ((LocalDate)data.get("seedStartDate")).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " " +
				"il sistema " + (equilibrate ? "bilanciato " + (remainder == 0 ? "perfetto " : "") +
				"(occorrenza effettiva: " + decimalFormat.format((combinationComponents * storage.size()) / (double)numbers.size()) +
				(numberOfCombosRequested == null ? ", richiesta: " + decimalFormat.format(occurrencesNumberRequested) : "") + ") " : "") +
				"e' composto da " + integerFormat.format(storage.size()) + " combinazioni " + "scelte su " + integerFormat.format(comboHandler.getSizeAsInt()) + " totali" +
					(discardedComboCounter.get() > 0 ? " (scartate: " + integerFormat.format(discardedComboCounter.get()) + ")": "")
			);
			boolean shouldBePlayed = random.nextBoolean();
			boolean shouldBePlayedAbsolutely = random.nextBoolean() && shouldBePlayed;
			if (magicCombinationMinNumber != null && magicCombinationMaxNumber != null) {
				Set<Integer> randomNumbers = new TreeSet<>();
				//Resettiamo il Random e simuliamo una generazione pulita
				basicDataSupplier.apply(extractionDate);
				moveRandomizerToLast(equilibrateFlagSupplier, effectiveRandomCounter, comboHandler);
				Iterator<Integer> randomIntegers = random.ints(magicCombinationMinNumber, magicCombinationMaxNumber + 1).iterator();
				while (randomNumbers.size() < combinationComponents) {
					randomNumbers.add(randomIntegers.next());
				}
				List<Integer> randomCombo = new ArrayList<>();
				for (Integer number : randomNumbers) {
					randomCombo.add(number);
				}
				storage.addLine();
				storage.addLine("Combinazione magica:");
				storage.addUnindexedCombo(randomCombo);
			}
			if (chooseRandom > 0) {
				//Resettiamo il Random e simuliamo una generazione pulita
				basicDataSupplier.apply(extractionDate);
				moveRandomizerToLast(equilibrateFlagSupplier, effectiveRandomCounter, comboHandler);
				Iterator<Integer> randomIntegers = random.ints(0, storageRef.size()).iterator();
				storage.addLine();
				storage.addLine("Combinazioni scelte casualmente dal sistema:");
				while (chooseRandom > 0) {
					storage.addUnindexedCombo(storage.getCombo(randomIntegers.next()));
					chooseRandom--;
				}
			}

			if (reportEnabled) {
				Map<String, Object> report = checkQuality(storageRef);
				if (reportDetailEnabled) {
					storage.addLine("\n");
					storage.addLine(
						(String)report.get("report.detail")
					);
				}
				storage.addLine("\n");
				storage.addLine(
					(String)report.get("report.summary")
				);
			}

			String text = "\nMr. Random suggerisce " + (shouldBePlayedAbsolutely? "assolutamente " : "") + "di " + (shouldBePlayed? "giocare" : "non giocare") + " il sistema per questo concorso";
			storage.addLine(text);
			if (avoidMode == 1 || avoidMode == 2) {
				if ((avoidMode == 1 && shouldBePlayed) || (avoidMode == 2 && shouldBePlayedAbsolutely)) {
					storageRef.printAll();
				} else {
					System.out.println(text);
					storageRef.delete();
				}
			} else {
				storageRef.printAll();
			}
		}
	}

	protected abstract Map<String, Object> checkQuality(Storage storageRef);

	private List<Integer> getNextCombo(
		AtomicReference<Iterator<List<Integer>>> combosIteratorWrapper,
		ComboHandler comboHandler,
		boolean[] alreadyComputed,
		AtomicLong indexGeneratorCallsCounter,
		AtomicInteger uniqueIndexCounter,
		AtomicInteger effectiveRandomCounter,
		AtomicLong discardedComboCounter
	) {
		Iterator<List<Integer>> combosIterator = combosIteratorWrapper.get();
		List<Integer> selectedCombo;
		if (combosIterator != null && combosIterator.hasNext()) {
			selectedCombo = combosIterator.next();
		} else {
			combosIterator = getNextCombos(
				comboHandler,
				alreadyComputed,
				indexGeneratorCallsCounter,
				uniqueIndexCounter
			).iterator();
			combosIteratorWrapper.set(combosIterator);
			selectedCombo = combosIterator.next();
		}
		effectiveRandomCounter.incrementAndGet();
		if (selectedCombo != null) {
			if (combinationFilter.test(selectedCombo)) {
				return selectedCombo;
			} else {
				discardedComboCounter.incrementAndGet();
			}
		}
		return null;
	}

	private void moveRandomizerToLast(BooleanSupplier equilibrateFlagSupplier, AtomicInteger effectiveRandomCounter,
			ComboHandler comboHandler) {
		equilibrateFlagSupplier.getAsBoolean();
		long browsedCombo = effectiveRandomCounter.get();
		while (browsedCombo-- > 0) {
			random.nextInt(comboHandler.getSizeAsInt());
		}
	}

	private Map<String, Object> resetRandomizer(Supplier<List<Integer>> numberSupplier, LocalDate extractionDate) {
		Map<String, Object> data = adjustSeed(extractionDate);
		data.put("numbersToBePlayed", numberSupplier.get());
		return data;
	}

	private List<List<Integer>> getNextCombos(
		ComboHandler comboHandler,
		boolean[] alreadyComputed,
		AtomicLong indexGeneratorCallsCounter,
		AtomicInteger uniqueIndexCounter
	) {
		List<Integer> effectiveRandomIndexes = new ArrayList<>();
		Set<Integer> randomIndexesToBeProcessed = new HashSet<>();
		Integer size = comboHandler.getSizeAsInt();
		int randomCollSize = Math.min(size, 10_000_000);
		while (effectiveRandomIndexes.size() < randomCollSize) {
			Integer idx = comboIndexSupplier.apply(size);
			indexGeneratorCallsCounter.incrementAndGet();
			if (!alreadyComputed[idx]) {
				effectiveRandomIndexes.add(idx);
				randomIndexesToBeProcessed.add(idx);
				uniqueIndexCounter.incrementAndGet();
				alreadyComputed[idx] = true;
			} else {
				effectiveRandomIndexes.add(null);
			}
		}
		System.out.println(
			formatter.format(LocalDateTime.now()) +
			" - " + integerFormat.format(uniqueIndexCounter.get()) + " unique indexes generated on " +
			integerFormat.format(indexGeneratorCallsCounter.get()) + " calls. " +
			integerFormat.format(randomIndexesToBeProcessed.size()) + " indexes will be processed in the current iteration."
		);
		if (size <= uniqueIndexCounter.get() && randomIndexesToBeProcessed.isEmpty()) {
			throw new AllRandomNumbersHaveBeenGeneratedException();
		}
		Map<Integer, List<Integer>> indexForCombos = comboHandler.find(randomIndexesToBeProcessed, true);
		List<List<Integer>> combos = new ArrayList<>();
		for (Integer index : effectiveRandomIndexes) {
			List<Integer> combo = indexForCombos.get(index);
			combos.add(combo);
		}
		return combos;
	}

	protected boolean containsOneOf(List<Integer> overRatioNumbers, List<Integer> selectedCombo) {
		return overRatioNumbers.stream().anyMatch(element -> selectedCombo.contains(element));
	}

	protected void incrementOccurences(Map<Integer, AtomicInteger> occurrences, List<Integer> selectedCombo) {
		for (Integer number : selectedCombo) {
			AtomicInteger counter = occurrences.computeIfAbsent(number, key -> new AtomicInteger(0));
			counter.incrementAndGet();
		}
	}

	protected static String toString(List<Integer> numbers, int[] indexes) {
		return String.join(
			"\t",
			Arrays.stream(indexes)
			.map(numbers::get)
		    .mapToObj(String::valueOf)
		    .collect(Collectors.toList())
		);
	}

	public Runnable getExecutor() {
		return executor;
	}

	protected String getExtractionArchiveStartDate() {
		return extractionArchiveStartDate != null ? extractionArchiveStartDate : getDefaultExtractionArchiveStartDate();
	}

	public abstract String getDefaultExtractionArchiveStartDate();

	protected abstract List<Map<String,List<Integer>>> getAllChosenNumbers();

	protected abstract List<Map<String,List<Integer>>> getAllDiscardedNumbers();

	protected abstract List<LocalDate> forWeekOf(LocalDate dayOfWeek);

	public abstract Map<String, Object> adjustSeed(LocalDate extractionDate);

	public abstract LocalDate computeNextExtractionDate(LocalDate startDate, boolean incrementIfExpired);

	protected abstract int getIncrementDays(LocalDate startDate);

	protected abstract Function<String, Function<Integer, Function<Integer, Iterator<Integer>>>> getNumberGeneratorFactory();

}

class AllRandomNumbersHaveBeenGeneratedException extends RuntimeException {

	private static final long serialVersionUID = 7009851378700603746L;

	public AllRandomNumbersHaveBeenGeneratedException() {
		super("All random numbers have been generated");
	}

}