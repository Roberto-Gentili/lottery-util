package org.rg.game.lottery.application;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PrimitiveIterator.OfLong;
import java.util.Properties;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.rg.game.core.CollectionUtils;
import org.rg.game.core.ConcurrentUtils;
import org.rg.game.core.IOUtils;
import org.rg.game.core.Info;
import org.rg.game.core.LogUtils;
import org.rg.game.core.MathUtils;
import org.rg.game.core.NetworkUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEPremium;
import org.rg.game.lottery.engine.SEStats;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

public class SEIntegralSystemAnalyzer extends Shared {
	private static List<Function<String, Record>> recordLoaders;
	private static List<Function<String, Consumer<Record>>> recordWriters;
	private static List<Function<String, Consumer<Record>>> localRecordWriters;


	public static void main(String[] args) throws IOException {
		long startTime = System.currentTimeMillis();
		try {
			recordWriters = new ArrayList<>();
			recordLoaders = new ArrayList<>();
			localRecordWriters = new ArrayList<>();
			addFirebaseRecordLoaderAndWriter();
		} catch (NoSuchElementException exc) {
			LogUtils.INSTANCE.info(exc.getMessage());
		} catch (Throwable exc) {
			LogUtils.INSTANCE.error(exc, "Unable to connect to Firebase");
		} finally {
			addDefaultRecordLoader();
			addDefaultRecordWriter();
			addJSONRecordLoader();
			addJSONRecordWriter();
		}

		String[] configurationFileFolders = ResourceUtils.INSTANCE.pathsFromSystemEnv(
			"working-path.integral-system-analysis.folder",
			"WORKING-PATH_INTEGRAL-SYSTEM-ANALYSIS_FOLDER",
			"resources.integral-system-analysis.folder",
			"RESOURCES_INTEGRAL_SYSTEM_ANALYSIS_FOLDER"
		);
		LogUtils.INSTANCE.info("Set configuration files folder to " + String.join(", ", configurationFileFolders) + "\n");
		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				"se-integral-systems-analysis", "properties",
				configurationFileFolders
			);
		String taskMaxParallel = Optional.ofNullable(System.getenv().get("tasks.max-parallel")).orElseGet(() -> System.getenv().get("TASKS_MAX_PARALLEL"));
		int maxParallelTasks = Optional.ofNullable(taskMaxParallel).map(Integer::valueOf)
				.orElseGet(() -> Math.max((Runtime.getRuntime().availableProcessors() / 2) - 1, 1));
		Collection<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
		boolean onlyShowComputed = false;
		String timeoutRawValue = null;
		String indexRawValue = null;
		for (String arg : args) {
			if (arg != null) {
				if (arg.contains("onlyShowComputed")) {
					onlyShowComputed = true;
					LogUtils.INSTANCE.info("Analysis disabled");
				} else if (arg.contains("timeout")) {
					timeoutRawValue = arg.split("=")[1];
				} else if (arg.contains("index.mode")) {
					indexRawValue = arg.split("=")[1];
				}
			}
		}
		if (timeoutRawValue == null) {
			timeoutRawValue = Optional.ofNullable(System.getenv().get("timeout"))
				.orElseGet(() -> System.getenv().get("TIMEOUT"));
		}
		if (timeoutRawValue != null) {
			LogUtils.INSTANCE.info("Set timeout to " + timeoutRawValue + " seconds");
			long timeout = Long.valueOf(timeoutRawValue);
			Thread exiter = new Thread(() -> {
				long elapsedTimeFromStart = System.currentTimeMillis() - startTime;
				long effectiveTimeout = (timeout * 1000) - elapsedTimeFromStart;
				if (effectiveTimeout > 0) {
					try {
						Thread.sleep(effectiveTimeout);
					} catch (InterruptedException e) {

					}
				}
				LogUtils.INSTANCE.info("Timeout reached");
				System.exit(0);
			});
			exiter.setDaemon(true);
			exiter.start();
		}
		Integer indexMode = indexRawValue != null? Integer.valueOf(indexRawValue) : Integer.valueOf(-1);
		if (indexRawValue == null) {
			indexRawValue = Optional.ofNullable(System.getenv().get("index.mode"))
					.orElseGet(() -> System.getenv().get("INDEX_MODE"));
			indexMode = indexRawValue != null? Integer.valueOf(indexRawValue) : Integer.valueOf(-1);
		}
		if (indexMode > Integer.valueOf(-1)) {
			LogUtils.INSTANCE.info("Indexing mode: " + indexMode);
		}
		Integer indexModeFinal = indexMode;
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles)) {
			String[] enabledRawValues = config.getProperty("enabled", "false").split(";");
			boolean enabled = false;
			for (String enabledRawValue : enabledRawValues) {
				if (enabledRawValue.equals("true")) {
					enabled = true;
					break;
				} else if(enabledRawValue.toUpperCase().contains("onJDK".toUpperCase())) {
					if (Integer.valueOf(enabledRawValue.toUpperCase().replace("onJDK".toUpperCase(), "")).compareTo(Info.Provider.getInfoInstance().getVersion()) == 0) {
						enabled = true;
						break;
					}
				}
			}
			if (enabled) {
				Runnable task =
					onlyShowComputed ?
						() ->
							showComputed(config) :
					indexMode > Integer.valueOf(-1) ?
						() ->
							index(config, indexModeFinal):
						() ->
							analyze(config);
				if (!onlyShowComputed && CollectionUtils.INSTANCE.retrieveBoolean(config, "async", "false")) {
					ConcurrentUtils.INSTANCE.addTask(futures, task);
				} else {
					task.run();
				}
			}
			ConcurrentUtils.INSTANCE.waitUntil(futures, ft -> ft.size() >= maxParallelTasks);
		}
		futures.forEach(CompletableFuture::join);
		LogUtils.INSTANCE.warn("All activities are finished");

	}


	protected static void addFirebaseRecordLoaderAndWriter() throws FileNotFoundException, IOException {
		String firebaseUrl = Optional.ofNullable(System.getenv().get("integral-system-analysis.firebase.url"))
			.orElseGet(() -> System.getenv().get("INTEGRAL_SYSTEM_ANALYSIS_FIREBASE_URL"));
		if (firebaseUrl == null) {
			throw new NoSuchElementException("Firebase URL not set");
		}
		LogUtils.INSTANCE.info("Database URL " + firebaseUrl);
		InputStream serviceAccount;
		try {
			serviceAccount = new ByteArrayInputStream(
				Optional.ofNullable(System.getenv().get("integral-system-analysis.firebase.credentials"))
				.orElseGet(() -> System.getenv().get("INTEGRAL_SYSTEM_ANALYSIS_FIREBASE_CREDENTIALS")
			).getBytes());
			LogUtils.INSTANCE.info("Credentials loaded from integral-system-analysis.firebase.credentials");
		} catch (Throwable exc) {
			String credentialsFilePath =
				Paths.get(
					Optional.ofNullable(System.getenv().get("integral-system-analysis.firebase.credentials.file"))
						.orElseGet(() -> System.getenv().get("INTEGRAL_SYSTEM_ANALYSIS_FIREBASE_CREDENTIALS_FILE"))
				).normalize().toAbsolutePath().toString();
			serviceAccount =
				new FileInputStream(
					credentialsFilePath
				);
			LogUtils.INSTANCE.info("Credentials loaded from " + credentialsFilePath);
		}
		FirebaseOptions options = FirebaseOptions.builder()
			  .setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(serviceAccount))
			  .setDatabaseUrl(firebaseUrl)
			  .build();

		FirebaseApp.initializeApp(options);
		Firestore firestore = FirestoreClient.getFirestore();
		addFirebaseRecordLoader(firestore);
		addFirebaseRecordWriter(firestore);
	}


	protected static void addFirebaseRecordWriter(Firestore firestore) {
		recordWriters.add(
			(String key) -> record -> {
				DocumentReference recordAsDocumentWrapper =
					firestore.document("IntegralSystemStats/"+key);
					//firestore.collection("IntegralSystemStats").document(key);
				Map<String, Object> recordAsRawValue = new LinkedHashMap<>();
				recordAsRawValue.put("value", IOUtils.INSTANCE.writeToJSONFormat(record));
				try {
					recordAsDocumentWrapper.set(recordAsRawValue).get();
				} catch (Throwable exc) {
					//LogUtils.INSTANCE.error(exc, "Unable to store data to Firebase");
					Throwables.INSTANCE.throwException(exc);
				}
			}
		);
	}


	protected static void addFirebaseRecordLoader(Firestore firestore) {
		recordLoaders.add(
			(String key) -> {
				//LogUtils.INSTANCE.info("Loading " + basePath + "/" + key);
				DocumentReference recordAsDocumentWrapper =
					firestore.document("IntegralSystemStats/"+key);
				ApiFuture<DocumentSnapshot> ap = recordAsDocumentWrapper.get();
				DocumentSnapshot recordAsDocument;
				try {
					recordAsDocument = ap.get();
				} catch (Throwable exc) {
					return Throwables.INSTANCE.throwException(exc);
				}
				return readFromJson((String)recordAsDocument.get("value"));
			}
		);
	}


	protected static void addDefaultRecordWriter() {
		String basePath = PersistentStorage.buildWorkingPath("Analisi sistemi integrali");
		Function<String, Consumer<Record>> writer = (String key) -> record -> {
			try {
				IOUtils.INSTANCE.store(basePath, key, record);
			} catch (Throwable exc) {
				//LogUtils.INSTANCE.error(exc, "Unable to store data to file system");
				//Throwables.INSTANCE.throwException(exc);
			}
		};
		recordWriters.add(writer);
		localRecordWriters.add(writer);
	}


	protected static void addJSONRecordWriter() {
		String basePath = PersistentStorage.buildWorkingPath("Analisi sistemi integrali");
		Function<String, Consumer<Record>> writer = (String key) -> record -> {
			try {
				IOUtils.INSTANCE.writeToJSONPrettyFormat(new File(basePath + "/" + key + ".json"), record);
			} catch (Throwable exc) {
				//LogUtils.INSTANCE.error(exc, "Unable to store data to file system");
				//Throwables.INSTANCE.throwException(exc);
			}
		};
		recordWriters.add(writer);
		localRecordWriters.add(writer);
	}



	protected static void addDefaultRecordLoader() {
		String basePath = PersistentStorage.buildWorkingPath("Analisi sistemi integrali");
		recordLoaders.add(
			(String key) -> {
				return
					IOUtils.INSTANCE.load(basePath, key);
			}
		);
	}

	protected static void addJSONRecordLoader() {
		String basePath = PersistentStorage.buildWorkingPath("Analisi sistemi integrali");
		recordLoaders.add(
			(String key) -> {
				return readFromJson(
					IOUtils.INSTANCE.fileToString(
							basePath + "/" + key + ".json",
							StandardCharsets.UTF_8
						)
					);
			}
		);
	}


	protected static void showComputed(Properties config) {
		ProcessingContext processingContext = new ProcessingContext(config);
		writeRecordToLocal(processingContext.cacheKey, processingContext.record);
		if (processingContext.record.data != null && !processingContext.record.data.isEmpty() &&
			processingContext.record.data.size() >= processingContext.rankSize
		) {
			//Sceglie una combinazione casuale fra quelle in classifica
			chooseAndPrintNextCompetitionSystem(processingContext.record, processingContext.rankSize);
		}
		printData(processingContext.record, false);
		LogUtils.INSTANCE.info(
			MathUtils.INSTANCE.format(processedSystemsCounter(processingContext.record)) + " of " +
			MathUtils.INSTANCE.format(processingContext.comboHandler.getSize()) +
			" systems processed\n"
		);
	}


	protected static void index(Properties config, Integer indexMode) {
		ProcessingContext processingContext = new ProcessingContext(config);
		int processedBlock = 0;
		for (Block currentBlock : processingContext.record.blocks) {
			if (currentBlock.indexes == null && indexMode.compareTo(0) > 0) {
				currentBlock.counter = currentBlock.start;
				currentBlock.indexes = processingContext.comboHandler.computeIndexes(currentBlock.start);
				List<Integer> combo = processingContext.comboHandler.toCombo(currentBlock.indexes);
				tryToAddCombo(
					processingContext,
					combo,
					computePremiums(processingContext, combo)
				);
				mergeAndStore(
					processingContext.cacheKey,
					processingContext.systemsRank,
					processingContext.record,
					currentBlock,
					processingContext.rankSize
				);
			} else if (currentBlock.indexes != null && indexMode.compareTo(0) == 0) {
				currentBlock.counter = null;
				currentBlock.indexes = null;
				writeRecord(processingContext.cacheKey, processingContext.record);
			}
			if (currentBlock.counter != null && currentBlock.counter.compareTo(currentBlock.start) < 0 && currentBlock.counter.compareTo(currentBlock.end) > 0) {
				LogUtils.INSTANCE.warn("Unaligned block: " + currentBlock);
			}
			processedBlock++;
			if (processedBlock % 10 == 0 || processedBlock == processingContext.record.blocks.size()) {
				LogUtils.INSTANCE.info(
					MathUtils.INSTANCE.format(processedBlock) + " of " +
					MathUtils.INSTANCE.format(processingContext.record.blocks.size()) + " blocks have been indexed"
				);
			}
		}
		LogUtils.INSTANCE.info(
			"All " + MathUtils.INSTANCE.format(processingContext.record.blocks.size()) + " blocks are indexed"
		);
		printData(processingContext.record, true);
	}


	protected static void analyze(Properties config) {
		ProcessingContext processingContext = new ProcessingContext(config);
		boolean printBlocks = CollectionUtils.INSTANCE.retrieveBoolean(config, "log.print.blocks", "true");
		while (!processingContext.assignedBlocks.isEmpty()) {
			AtomicReference<Block> currentBlockWrapper = new AtomicReference<>();
			AtomicBoolean blockNotAlignedWrapper = new AtomicBoolean(false);
			Supplier<Block> blockSupplier = () -> {
				Block block = currentBlockWrapper.get();
				if (block != null && block.counter != null && block.counter.compareTo(block.end) >= 0) {
					currentBlockWrapper.set(block = null);
				}
				if (block == null) {
					Iterator<Block> blocksIterator =  processingContext.assignedBlocks.iterator();
					while (blocksIterator.hasNext()) {
						block = blocksIterator.next();
						blocksIterator.remove();
						if (block.counter != null && block.counter.compareTo(block.end) >= 0) {
							continue;
						}
						currentBlockWrapper.set(block);
						blockNotAlignedWrapper.set(true);
						LogUtils.INSTANCE.info("Received in assignment " + block);
						break;
					}
				}
				return block;
			};
			Block absignedBlock = blockSupplier.get();
			BigInteger sizeOfIntegralSystemMatrix = processingContext.comboHandler.getSize();
			String sizeOfIntegralSystemMatrixAsString = MathUtils.INSTANCE.format(sizeOfIntegralSystemMatrix);
			processingContext.comboHandler.iterateFrom(
				processingContext.comboHandler.new IterationData(absignedBlock.indexes, absignedBlock.counter),
				iterationData -> {
					Block currentBlock = blockSupplier.get();
					if (currentBlock == null) {
						iterationData.terminateIteration();
						return;
					}
					if (blockNotAlignedWrapper.get()) {
						if (iterationData.getCounter().compareTo(currentBlock.start) < 0 || iterationData.getCounter().compareTo(currentBlock.end) > 0) {
							if (iterationData.getCounter().mod(processingContext.modderForSkipLog).compareTo(BigInteger.ZERO) == 0) {
								LogUtils.INSTANCE.info(
									"Skipped " + MathUtils.INSTANCE.format(iterationData.getCounter()) +
									" of " + sizeOfIntegralSystemMatrixAsString + " systems"
								);
							}
							return;
						}
						BigInteger currentBlockCounter = currentBlock.counter;
						if (currentBlockCounter != null) {
							if (currentBlockCounter.compareTo(iterationData.getCounter()) > 0) {
								return;
							}
							if (currentBlockCounter.compareTo(iterationData.getCounter()) == 0) {
								LogUtils.INSTANCE.info(
									"Skipped " + MathUtils.INSTANCE.format(iterationData.getCounter()) + " of " + sizeOfIntegralSystemMatrixAsString + " systems\n" +
									"Cache succesfully restored, starting from the " + MathUtils.INSTANCE.format(iterationData.getCounter()) + " system. " +
									MathUtils.INSTANCE.format(remainedSystemsCounter(processingContext.record)) + " systems remained."
								);
								printDataIfChanged(
									processingContext.record,
									processingContext.previousLoggedRankWrapper,
									printBlocks
								);
								return;
							}
						}
						blockNotAlignedWrapper.set(false);
					}
					currentBlock.counter = iterationData.getCounter();
					//Operazione spostata prima dell'operazione di store per motivi di performance:
					//in caso di anomalie decomentarla e cancellare la riga più in basso
					//currentBlock.indexes = iterationData.copyOfIndexes();
					List<Integer> combo = iterationData.getCombo();
					Map<Number, Integer> allPremiums = computePremiums(processingContext, combo);
					if (filterCombo(allPremiums, Premium.TYPE_FIVE)) {
						tryToAddCombo(processingContext, combo, allPremiums);
					}
					if (iterationData.getCounter().mod(processingContext.modderForAutoSave).compareTo(BigInteger.ZERO) == 0 || iterationData.getCounter().compareTo(currentBlock.end) == 0) {
						currentBlock.indexes = iterationData.copyOfIndexes(); //Ottimizzazione: in caso di anomalie eliminare questa riga e decommentare la riga più in alto (vedere commento)
						mergeAndStore(
							processingContext.cacheKey,
							processingContext.systemsRank,
							processingContext.record,
							currentBlock,
							processingContext.rankSize
						);
						printDataIfChanged(
							processingContext.record,
							processingContext.previousLoggedRankWrapper,
							printBlocks
						);
						LogUtils.INSTANCE.info(
							MathUtils.INSTANCE.format(processedSystemsCounter(processingContext.record)) + " of " +
							sizeOfIntegralSystemMatrixAsString + " systems have been analyzed"
						);
		    		}
				}
			);
			if (processingContext.assignedBlocks.isEmpty()) {
				processingContext.assignedBlocks.addAll(retrieveAssignedBlocks(config, processingContext.record));
			}
		}
		printData(processingContext.record, printBlocks);
		//LogUtils.INSTANCE.info(processedSystemsCounterWrapper.get() + " of combinations analyzed");
	}


	protected static boolean filterCombo(Map<Number, Integer> allPremiums, Integer premiumType) {
		boolean highWinningFound = false;
		for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
			if (premiumTypeAndCounter.getKey().doubleValue() > premiumType.doubleValue() && premiumTypeAndCounter.getValue() > 0) {
				highWinningFound = true;
				break;
			}
		}
		return highWinningFound;
	}


	protected static boolean tryToAddCombo(ProcessingContext processingContext, List<Integer> combo,
			Map<Number, Integer> allPremiums) {
		Map.Entry<List<Integer>, Map<Number, Integer>> addedItem = new AbstractMap.SimpleEntry<>(combo, allPremiums);
		boolean addedItemFlag = processingContext.systemsRank.add(addedItem);
		if (processingContext.systemsRank.size() > processingContext.rankSize) {
			Map.Entry<List<Integer>, Map<Number, Integer>> removedItem = processingContext.systemsRank.pollLast();
			if (removedItem != addedItem) {
				//store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
				LogUtils.INSTANCE.info(
					"Replaced data from rank:\n\t" + ComboHandler.toString(removedItem.getKey(), ", ") + ": " + removedItem.getValue() + "\n" +
					"\t\twith\n"+
					"\t" + ComboHandler.toString(addedItem.getKey(), ", ") + ": " + addedItem.getValue()
				);
			}
			return true;
		} else if (addedItemFlag) {
			//store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
			LogUtils.INSTANCE.info("Added data to rank: " + ComboHandler.toString(combo, ", ") + ": " + allPremiums);
			return true;
		}
		return false;
	}


	protected static Map<Number, Integer> computePremiums(ProcessingContext processingContext, List<Integer> combo) {
		Map<Number, Integer> allPremiums = new LinkedHashMap<>();
		for (Number premiumType : processingContext.orderedPremiumsToBeAnalyzed) {
			allPremiums.put(premiumType, 0);
		}
		for (List<Integer> winningComboWithSuperStar : processingContext.allWinningCombosWithJollyAndSuperstar) {
			Map<Number, Integer> premiums = SEPremium.checkIntegral(combo, winningComboWithSuperStar);
			for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
				Number premiumType = premiumTypeAndCounter.getKey();
				Integer premiumCounter = premiums.get(premiumType);
				if (premiumCounter != null) {
					allPremiums.put(premiumType, allPremiums.get(premiumType) + premiumCounter);
				}
			}
		}
		return allPremiums;
	}


	protected static Record readFromJson(String recordAsFlatRawValue) {
		if (recordAsFlatRawValue == null) {
			return null;
		}
		Map<String, Object> recordAsRawValue = IOUtils.INSTANCE.readFromJSONFormat(recordAsFlatRawValue, Map.class);
		Collection<Block> blocks = new ArrayList<>();
		for (Map<String, Object> blocksAsRawValue : (Collection<Map<String, Object>>)recordAsRawValue.get("blocks")) {
			int[] indexes =
				(int[])Optional.ofNullable((Collection<Integer>)blocksAsRawValue.get("indexes"))
				.map(numbers -> numbers.stream().mapToInt(Integer::intValue).toArray()).orElseGet(() -> null);
			blocks.add(
				new Block(
					new BigInteger(blocksAsRawValue.get("start").toString()),
					new BigInteger(blocksAsRawValue.get("end").toString()),
					Optional.ofNullable(
						blocksAsRawValue.get("counter")
					).map(Object::toString).map(BigInteger::new).orElseGet(() -> null),
					indexes
				)
			);
		}
		Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data = new ArrayList<>();
		for (Map<String, Map<String, Integer>> comboForResultAsRawValue : (Collection<Map<String, Map<String, Integer>>>) recordAsRawValue.get("data")) {
			Map.Entry<String, Map<String, Integer>> comboForResultAsRawValueEntry = comboForResultAsRawValue.entrySet().iterator().next();
			Map<Number, Integer> premiums = new LinkedHashMap<>();
			for (Map.Entry<String, Integer> premium : comboForResultAsRawValueEntry.getValue().entrySet()) {
				premiums.put(Premium.parseType(premium.getKey()), premium.getValue());
			}
			data.add(
				new AbstractMap.SimpleEntry<>(
					ComboHandler.fromString(comboForResultAsRawValueEntry.getKey().replaceAll("\\[|\\]", "")),
					premiums
				)
			);
		}
		return new Record(blocks, data);
	}


	protected static void chooseAndPrintNextCompetitionSystem(Record cacheRecord, int rankSize) {
		LocalDate nextExtractionDate = SELotteryMatrixGeneratorEngine.DEFAULT_INSTANCE.computeNextExtractionDate(LocalDate.now(), false);
		Map.Entry<LocalDate, Long> seedData = getSEAllStats().getSeedData(nextExtractionDate);
		seedData.getValue();
		Long size = cacheRecord.blocks.stream().reduce((first, second) -> second)
		  .orElse(null).end.longValue();
		Random random = new Random(seedData.getValue());
		OfLong randomizer = random.longs(1L, size + 1).iterator();
		long nextLong = -1;
		while (nextLong > rankSize || nextLong < 0) {
			nextLong = randomizer.nextLong();
		}
		Map.Entry<List<Integer>, Map<Number, Integer>> combo = new ArrayList<>(cacheRecord.data).get(Long.valueOf(nextLong).intValue());
		ComboHandler cH = new ComboHandler(combo.getKey(), 6);
		LogUtils.INSTANCE.info(
			"La combinazione scelta per il concorso " + seedData.getValue() + " del " +
			TimeUtils.defaultLocalDateFormat.format(nextExtractionDate) + " è:\n\t" + ComboHandler.toString(combo.getKey(), ", ") +
			"\nposizionata al " + nextLong + "° posto. Il relativo sistema è:"
		);
		cH.iterate(iterationData -> {
			LogUtils.INSTANCE.info("\t" + ComboHandler.toString(iterationData.getCombo()));
		});
	}


	protected static String buildCacheKey(ComboHandler comboHandler, SEStats sEStats, String premiumsToBeAnalyzed, int rankSize) {
		return "[" + MathUtils.INSTANCE.format(comboHandler.getSize()).replace(".", "_") + "][" + comboHandler.getCombinationSize() + "]" +
				"[" + premiumsToBeAnalyzed.replace(".", "_") + "]" + "[" + rankSize + "]" +
				"[" + TimeUtils.getAlternativeDateFormat().format(sEStats.getStartDate()) + "]" +
				"[" + TimeUtils.getAlternativeDateFormat().format(sEStats.getEndDate()) + "]";
	}


	protected static BigInteger processedSystemsCounter(Record record) {
		BigInteger processed = BigInteger.ZERO;
		for (Block block : record.blocks) {
			if (block.counter != null) {
				processed = processed.add(block.counter.subtract(block.start.subtract(BigInteger.ONE)));
			}
		}
		return processed;
	}


	protected static BigInteger remainedSystemsCounter(Record record) {
		BigInteger processedSystemsCounter = processedSystemsCounter(record);
		Block latestBlock = CollectionUtils.INSTANCE.getLastElement(record.blocks);
		return latestBlock.end.subtract(processedSystemsCounter);
	}

	protected static BigInteger systemsCounter(Record record) {
		Block latestBlock = CollectionUtils.INSTANCE.getLastElement(record.blocks);
		return latestBlock.end;
	}


	protected static Record prepareCacheRecord(
		String cacheKey, ComboHandler cH,
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank
	){
		Record cacheRecordTemp = loadRecord(cacheKey);
		if (cacheRecordTemp != null) {
			systemsRank.addAll(cacheRecordTemp.data);
		} else {
			cacheRecordTemp = new Record();
		}
		if (cacheRecordTemp.blocks == null) {
			long blockNumbers = cH.getCombinationSize() * 2;
			BigInteger aHundredMillion = BigInteger.valueOf(100_000_000L);
			if (cH.getSize().compareTo(aHundredMillion) > 0) {
				blockNumbers = cH.getSize().divide(aHundredMillion).longValue();
			}
			cacheRecordTemp.blocks = divide(cH.getSize(), blockNumbers);
		}
		return cacheRecordTemp;
	}


	protected static List<Block> retrieveAssignedBlocks(Properties config, Record cacheRecordTemp) {
		String blockAssignees = config.getProperty("blocks.assegnee");
		Collection<Block> blocks = new LinkedHashSet<>();
		boolean random = false;
		if (blockAssignees != null) {
			String thisHostName = NetworkUtils.INSTANCE.thisHostName();
			for (String blockAssignee : blockAssignees.replaceAll("\\s+","").split(";")) {
				String[] blockAssigneeInfo = blockAssignee.split(":");
				if (blockAssigneeInfo.length > 1 && blockAssigneeInfo[1].contains("random")) {
					random = true;
					blockAssigneeInfo[1] = blockAssigneeInfo[1].replace("random", "").replace("[", "").replace("]", "");
				}
				if (blockAssigneeInfo[0].equalsIgnoreCase(thisHostName) || blockAssigneeInfo[0].equals("all")) {
					if (blockAssigneeInfo[1].isEmpty() || blockAssigneeInfo[1].equals("all")) {
						blocks.addAll(cacheRecordTemp.blocks);
					} else {
						blocks.clear();
						for (String blockIndex : blockAssigneeInfo[1].split(",")) {
							if (blockIndex.equalsIgnoreCase("odd")) {
								blocks.addAll(CollectionUtils.INSTANCE.odd(cacheRecordTemp.blocks));
							} else if (blockIndex.equalsIgnoreCase("even")) {
								blocks.addAll(CollectionUtils.INSTANCE.even(cacheRecordTemp.blocks));
							} else if (blockIndex.contains("/")) {
								String[] subListsInfo = blockIndex.split("/");
								List<List<Block>> subList =
									CollectionUtils.INSTANCE.toSubLists((List<Block>)cacheRecordTemp.blocks,
										Double.valueOf(Math.ceil(((List<Block>)cacheRecordTemp.blocks).size() / Double.valueOf(subListsInfo[1]))).intValue()
									);
								blocks.addAll(subList.get(Integer.valueOf(subListsInfo[0]) - 1));
							} else {
								blocks.add(cacheRecordTemp.getBlock(Integer.valueOf(blockIndex) - 1));
							}
						}
						break;
					}
				} else if (blockAssigneeInfo[0].contains("random")) {
					blocks.addAll(cacheRecordTemp.blocks);
					random = true;
				}
			}
		} else {
			blocks.addAll(cacheRecordTemp.blocks);
		}
		Iterator<Block> blocksIterator = blocks.iterator();
		while (blocksIterator.hasNext()) {
			Block block = blocksIterator.next();
			BigInteger counter = block.counter;
			if (counter != null && counter.compareTo(block.end) == 0) {
				blocksIterator.remove();
			}
		}
		List<Block> toBeProcessed = new ArrayList<>(blocks);
		if (random) {
			Collections.shuffle(toBeProcessed) ;
		}
		return toBeProcessed;
	}


	protected static TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> buildDataCollection(Number[] orderedPremiumsToBeAnalyzed) {
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> bestSystems = new TreeSet<>((itemOne, itemTwo) -> {
			if (itemOne != itemTwo) {
				for(Number type : orderedPremiumsToBeAnalyzed) {
					int comparitionResult = itemOne.getValue().getOrDefault(type, 0).compareTo(itemTwo.getValue().getOrDefault(type, 0));
					if (comparitionResult != 0) {
						return comparitionResult * -1;
					}
				}
				for (int i = 0; i < Math.max(itemOne.getKey().size(), itemTwo.getKey().size()); i++) {
					int numberComparition = itemOne.getKey().get(i).compareTo(itemTwo.getKey().get(i));
					if (numberComparition != 0) {
						return numberComparition * -1;
					}
				}
			}
			return 0;
		});
		return bestSystems;
	}


	protected static void printData(
		Record record,
		boolean showBlockInfo
	) {
		String currentRank = String.join(
			"\n\t",
			record.data.stream().map(entry ->
				ComboHandler.toString(entry.getKey(), ", ") + ": " + Premium.toString(entry.getValue(), "=", ", ")
			).collect(Collectors.toList())
		);
		String currentLog = "";
		if (showBlockInfo) {
			currentLog += "\nBlocks (size: " + record.blocks.size() + ") status:\n" +
				"\t" + String.join(
					"\n\t",
					record.blocks.stream().map(Object::toString).collect(Collectors.toList())
				) + "\n";
		}
		currentLog += "Rank (size: " + record.data.size() + "):\n" +
			"\t" + currentRank + "\n";				;
		LogUtils.INSTANCE.info(currentLog);
	}


	protected static void printDataIfChanged(
		Record record,
		AtomicReference<String> previousLoggedRankWrapper,
		boolean showBlockInfo
	) {
		String currentRank = String.join(
			"\n\t",
			record.data.stream().map(entry ->
				ComboHandler.toString(entry.getKey(), ", ") + ": " + Premium.toString(entry.getValue(), "=", ", ")
			).collect(Collectors.toList())
		);
		String currentLog = "";
		if (showBlockInfo) {
			currentLog += "\nBlocks (size: " + record.blocks.size() + ") status:\n" +
				"\t" + String.join(
					"\n\t",
					record.blocks.stream().map(Object::toString).collect(Collectors.toList())
				) + "\n";
		}
		currentLog += "Rank (size: " + record.data.size() + "):\n" +
			"\t" + currentRank + "\n";
		String previousLoggedRank = previousLoggedRankWrapper.get();
		if (previousLoggedRank == null || !previousLoggedRank.equals(currentRank)) {
			LogUtils.INSTANCE.info(currentLog);
		}
		previousLoggedRankWrapper.set(currentRank);
	}


	protected static Record loadRecord(String cacheKey) {
		List<Throwable> exceptions = new ArrayList<>();
		for (Function<String, Record> recordLoader : recordLoaders) {
			try {
				return recordLoader.apply(cacheKey);
			} catch (Throwable exc) {
				LogUtils.INSTANCE.error(exc, "Unable to load data:");
				exceptions.add(exc);
				if (exceptions.size() == recordLoaders.size()) {
					return Throwables.INSTANCE.throwException(exceptions.get(0));
				}
			}
		}
		return null;
	}


	protected static void writeRecord(String cacheKey, Record toBeCached) {
		writeRecordTo(cacheKey, toBeCached, recordWriters);
	}

	protected static void writeRecordToLocal(String cacheKey, Record toBeCached) {
		writeRecordTo(cacheKey, toBeCached, localRecordWriters);
	}


	protected static void writeRecordTo(
		String cacheKey,
		Record toBeCached,
		List<Function<String,
		Consumer<Record>>> recordWriters
	) {
		List<Throwable> exceptions = new ArrayList<>();
		for (Function<String, Consumer<Record>> recordWriter : recordWriters) {
			try {
				recordWriter.apply(cacheKey).accept(toBeCached);
			} catch (Throwable exc) {
				LogUtils.INSTANCE.error(exc, "Unable to store data");
				exceptions.add(exc);
				if (exceptions.size() == recordLoaders.size()) {
					Throwables.INSTANCE.throwException(exceptions.get(0));
				}
			}
		}
	}


	private static void mergeAndStore(
		String cacheKey,
		TreeSet<Entry<List<Integer>, Map<Number, Integer>>> systemsRank,
		Record toBeCached,
		Block currentBlock,
		int rankSize
	){
		Record cacheRecord = loadRecord(cacheKey);
		if (cacheRecord != null) {
			systemsRank.addAll(cacheRecord.data);
			List<Block> cachedBlocks = (List<Block>)cacheRecord.blocks;
			for (int i = 0; i < cachedBlocks.size(); i++) {
				Block toBeCachedBlock = ((List<Block>)toBeCached.blocks).get(i);
				if (currentBlock == toBeCachedBlock) {
					continue;
				}
				Block cachedBlock = cachedBlocks.get(i);
				BigInteger cachedBlockCounter = cachedBlock.counter;
				if (cachedBlockCounter != null && (toBeCachedBlock.counter == null || cachedBlockCounter.compareTo(toBeCachedBlock.counter) > 0)) {
					toBeCachedBlock.counter = cachedBlock.counter;
					toBeCachedBlock.indexes = cachedBlock.indexes;
				}
			}
		}
		while (systemsRank.size() > rankSize) {
			systemsRank.pollLast();
		}
		toBeCached.data = new ArrayList<>(systemsRank);
		writeRecord(cacheKey, toBeCached);
	}


	protected static void store(
		String basePath,
		String cacheKey,
		Record record,
		Record cacheRecord
	) {
		cacheRecord.data = new ArrayList<>(record.data);
		IOUtils.INSTANCE.store(basePath, cacheKey, cacheRecord);
	}


	public static List<Block> divide(BigInteger size, long blockNumber) {
		BigInteger blockSize = size.divide(BigInteger.valueOf(blockNumber));
		BigInteger remainedSize = size.mod(BigInteger.valueOf(blockNumber));
		List<Block> blocks = new ArrayList<>();
		BigInteger blockStart = BigInteger.ONE;
		for (int i = 0; i < blockNumber; i++) {
			BigInteger blockEnd = blockStart.add(blockSize.subtract(BigInteger.ONE));
			blocks.add(new Block(blockStart, blockEnd, null, null));
			blockStart = blockEnd.add(BigInteger.ONE);
		}
		if (remainedSize.compareTo(BigInteger.ZERO) != 0) {
			blocks.add(new Block(blockStart, blockStart.add(remainedSize.subtract(BigInteger.ONE)), null, null));
		}
		return blocks;
	}


	public static class Record implements Serializable {

		private static final long serialVersionUID = -5223969149097163659L;

		Record() {}

		Record(Collection<Block> blocks, Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data) {
			this.blocks = blocks;
			this.data = data;
		}

		@JsonProperty("blocks")
		private Collection<Block> blocks;

		@JsonProperty("data")
		private Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data;

		public Block getBlock(int index) {
			return ((List<Block>)blocks).get(index);
		}

	}

	public static class Block implements Serializable {

		private static final long serialVersionUID = 1725710713018555234L;

		@JsonProperty("start")
		private BigInteger start;

		@JsonProperty("end")
		private BigInteger end;

		@JsonProperty("counter")
		private BigInteger counter;

		@JsonProperty("indexes")
		private int[] indexes;

		public Block(BigInteger start, BigInteger end, BigInteger counter, int[] indexes) {
			this.start = start;
			this.end = end;
			this.counter = counter;
			this.indexes = indexes;
		}

		@Override
		public String toString() {
			return "Block [start=" + start + ", end=" + end + ", counter=" + counter + ", indexes="
					+ Arrays.toString(indexes) + "]";
		}


	}

	private static class ProcessingContext {
		private List<Block> assignedBlocks;
		private Record record;
		private Integer rankSize;
		private ComboHandler comboHandler;
		private BigInteger modderForSkipLog;
		private AtomicReference<String> previousLoggedRankWrapper;
		private Number[] orderedPremiumsToBeAnalyzed;
		private Collection<List<Integer>> allWinningCombosWithJollyAndSuperstar;
		private TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank;
		private BigInteger modderForAutoSave;
		private String cacheKey;

		private ProcessingContext(Properties config) {
			String premiumsToBeAnalyzed = config.getProperty(
				"rank.premiums",
				String.join(",", Premium.allTypesListReversed().stream().map(Object::toString).collect(Collectors.toList()))
			).replaceAll("\\s+","");
			orderedPremiumsToBeAnalyzed =
				Arrays.asList(
					premiumsToBeAnalyzed.split(",")
				).stream().map(Premium::parseType).toArray(Number[]::new);
			long combinationSize = Long.valueOf(config.getProperty("combination.components"));
			comboHandler = new ComboHandler(SEStats.NUMBERS, combinationSize);
			modderForSkipLog = BigInteger.valueOf(1_000_000_000);
			modderForAutoSave = new BigInteger(config.getProperty("autosave-every", "1000000"));
			rankSize = Integer.valueOf(config.getProperty("rank.size", "100"));
			SEStats sEStats = SEStats.get(
				config.getProperty("competition.archive.start-date"),
				config.getProperty("competition.archive.end-date")
			);
			allWinningCombosWithJollyAndSuperstar = sEStats.getAllWinningCombosWithJollyAndSuperstar().values();
			LogUtils.INSTANCE.info("All " + combinationSize + " based integral systems size (" + comboHandler.getNumbers().size() + " numbers): " +  MathUtils.INSTANCE.format(comboHandler.getSize()));
			cacheKey = buildCacheKey(comboHandler, sEStats, premiumsToBeAnalyzed, rankSize);
			systemsRank = buildDataCollection(orderedPremiumsToBeAnalyzed);
			record = prepareCacheRecord(
				cacheKey,
				comboHandler,
				systemsRank
			);
			assignedBlocks = retrieveAssignedBlocks(config, record);
			previousLoggedRankWrapper = new AtomicReference<>();
		}
	}

}
