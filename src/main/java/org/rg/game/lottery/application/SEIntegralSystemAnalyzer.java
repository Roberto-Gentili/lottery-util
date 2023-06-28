package org.rg.game.lottery.application;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.rg.game.core.CollectionUtils;
import org.rg.game.core.ConcurrentUtils;
import org.rg.game.core.IOUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.MathUtils;
import org.rg.game.core.NetworkUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.ComboHandler.IterationData;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SEPremium;
import org.rg.game.lottery.engine.SEStats;

import com.fasterxml.jackson.annotation.JsonProperty;

class SEIntegralSystemAnalyzer extends Shared {

	public static void main(String[] args) throws IOException {
		String[] configurationFileFolders = ResourceUtils.INSTANCE.pathsFromSystemEnv(
			"working-path.integral-system-analysis.folder",
			"resources.integral-system-analysis.folder"
		);
		LogUtils.info("Set configuration files folder to " + String.join(", ", configurationFileFolders) + "\n");
		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				"se-integral-systems-analysis", "properties",
				configurationFileFolders
			);
		int maxParallelTasks = Optional.ofNullable(System.getenv("tasks.max-parallel")).map(Integer::valueOf)
				.orElseGet(() -> Math.max((Runtime.getRuntime().availableProcessors() / 2) - 1, 1));
		Collection<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles)) {
			if (CollectionUtils.retrieveBoolean(config, "enabled", "false")) {
				Runnable task = () ->
					analyze(config);
				if (CollectionUtils.retrieveBoolean(config, "async", "false")) {
					ConcurrentUtils.addTask(futures, task);
				} else {
					task.run();
				}
			}
			ConcurrentUtils.waitUntil(futures, ft -> ft.size() >= maxParallelTasks);
		}
		futures.forEach(CompletableFuture::join);
	}

	protected static void analyze(Properties config) {
		long combinationSize = Long.valueOf(config.getProperty("combination.components"));
		ComboHandler comboHandler = new ComboHandler(IntStream.range(1, 91).boxed().collect(Collectors.toList()), combinationSize);
		BigInteger modderForSkipLog = BigInteger.valueOf(1_000_000_000);
		BigInteger modderForAutoSave = new BigInteger(config.getProperty("autosave-every", "1000000"));
		int rankSize = Integer.valueOf(config.getProperty("rank-size", "100"));
		SEStats sEStats = SEStats.get(
			config.getProperty("competition.archive.start-date"),
			config.getProperty("competition.archive.end-date")
		);
		Collection<List<Integer>> allWinningCombos = sEStats.getAllWinningCombosWithJollyAndSuperstar().values();
		LogUtils.info("All " + combinationSize + " based integral systems size (" + comboHandler.getNumbers().size() + " numbers): " +  MathUtils.INSTANCE.format(comboHandler.getSize()));
		String basePath = PersistentStorage.buildWorkingPath("Analisi sistemi integrali");
		String cacheKey = buildCacheKey(comboHandler, sEStats);
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank = buildDataCollection();
		Record cacheRecord = prepareCacheRecord(
			basePath,
			cacheKey,
			comboHandler,
			systemsRank
		);
		List<Block> assignedBlocks = retrieveAssignedBlocks(config, cacheRecord);
		while (!assignedBlocks.isEmpty()) {
			AtomicReference<Block> currentBlockWrapper = new AtomicReference<>();
			AtomicBoolean blockNotAlignedWrapper = new AtomicBoolean(false);
			Supplier<Block> blockSupplier = () -> {
				Block block = currentBlockWrapper.get();
				if (block != null && block.counter != null && block.counter.compareTo(block.end) >= 0) {
					currentBlockWrapper.set(block = null);
				}
				if (block == null) {
					Iterator<Block> blocksIterator =  assignedBlocks.iterator();
					while (blocksIterator.hasNext()) {
						block = blocksIterator.next();
						blocksIterator.remove();
						if (block.counter != null && block.counter.compareTo(block.end) >= 0) {
							continue;
						}
						currentBlockWrapper.set(block);
						blockNotAlignedWrapper.set(true);
						break;
					}
				}
				return block;
			};
			comboHandler.iterate(iterationData -> {
				Block currentBlock = blockSupplier.get();
				if (currentBlock == null) {
					iterationData.terminateIteration();
					return;
				}
				if (blockNotAlignedWrapper.get()) {
					if (iterationData.getCounter().compareTo(currentBlock.start) < 0 || iterationData.getCounter().compareTo(currentBlock.end) > 0) {
						if (iterationData.getCounter().mod(modderForSkipLog).compareTo(BigInteger.ZERO) == 0) {
							LogUtils.info("Skipped " + MathUtils.INSTANCE.format(iterationData.getCounter()) + " of systems");
						}
						return;
					}
					BigInteger currentBlockCounter = currentBlock.counter;
					if (currentBlockCounter != null) {
						if (currentBlockCounter.compareTo(iterationData.getCounter()) > 0) {
							return;
						}
						if (currentBlockCounter.compareTo(iterationData.getCounter()) == 0) {
							LogUtils.info("Skipped " + MathUtils.INSTANCE.format(iterationData.getCounter()) + " of systems");
							LogUtils.info(
								"Cache succesfully restored, starting from index " + MathUtils.INSTANCE.format(iterationData.getCounter()) + ". " +
								MathUtils.INSTANCE.format(remainedSystems(cacheRecord)) + " systems remained."
							);
							printData(cacheRecord);
							return;
						}
					}
					blockNotAlignedWrapper.set(false);
				}
				currentBlock.counter = iterationData.getCounter();
				List<Integer> combo = iterationData.getCombo();
				Map<Number, Integer> allPremiums = new LinkedHashMap<>();
				for (Number premiumType : Premium.allTypesReversed()) {
					allPremiums.put(premiumType, 0);
				}
				for (List<Integer> winningComboWithSuperStar : allWinningCombos) {
					Map<Number, Integer> premiums = SEPremium.checkIntegral(combo, winningComboWithSuperStar);
					for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
						Number premiumType = premiumTypeAndCounter.getKey();
						Integer premiumCounter = premiums.get(premiumType);
						if (premiumCounter != null) {
							allPremiums.put(premiumType, allPremiums.get(premiumType) + premiumCounter);
						}
					}
				}
				boolean highWinningFound = false;
				for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
					if (premiumTypeAndCounter.getKey().doubleValue() > Premium.TYPE_FIVE.doubleValue() && premiumTypeAndCounter.getValue() > 0) {
						highWinningFound = true;
						break;
					}
				}
				if (highWinningFound) {
					Map.Entry<List<Integer>, Map<Number, Integer>> addedItem = new AbstractMap.SimpleEntry<>(combo, allPremiums);
					boolean addedItemFlag = systemsRank.add(addedItem);
					if (systemsRank.size() > rankSize) {
						Map.Entry<List<Integer>, Map<Number, Integer>> removedItem = systemsRank.pollLast();
						if (removedItem != addedItem) {
							//store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
							LogUtils.info(
								"Replaced data from rank:\n\t" + ComboHandler.toString(removedItem.getKey(), ", ") + ": " + removedItem.getValue() + "\n" +
								"\t\twith\n"+
								"\t" + ComboHandler.toString(addedItem.getKey(), ", ") + ": " + addedItem.getValue()
							);
						}
					} else if (addedItemFlag) {
						//store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
						LogUtils.info("Added data to rank: " + ComboHandler.toString(combo, ", ") + ": " + allPremiums);
					}
				}
				if (iterationData.getCounter().mod(modderForAutoSave).compareTo(BigInteger.ZERO) == 0 || iterationData.getCounter().compareTo(currentBlock.end) == 0) {
					store(basePath, cacheKey, iterationData, systemsRank, cacheRecord, currentBlock, rankSize);
					printData(cacheRecord);
					LogUtils.info(MathUtils.INSTANCE.format(processedSystemsCounter(cacheRecord)) + " of systems have been processed");
	    		}
			});
		}
		if (assignedBlocks.isEmpty()) {
			assignedBlocks.addAll(retrieveAssignedBlocks(config, cacheRecord));
		}
		//LogUtils.info(processedSystemsCounterWrapper.get() + " of combinations analyzed");
	}

	protected static String buildCacheKey(ComboHandler comboHandler, SEStats sEStats) {
		return "[" + MathUtils.INSTANCE.format(comboHandler.getSize()).replace(".", "_") + "][" + comboHandler.getCombinationSize() + "]" +
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

	protected static BigInteger remainedSystems(Record record) {
		BigInteger processedSystemsCounter = processedSystemsCounter(record);
		Block latestBlock = CollectionUtils.getLastElement(record.blocks);
		return latestBlock.end.subtract(processedSystemsCounter);
	}

	protected static Record prepareCacheRecord(
		String basePath, String cacheKey, ComboHandler cH,
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank
	) {
		Record cacheRecordTemp = IOUtils.INSTANCE.load(cacheKey, basePath);
		if (cacheRecordTemp != null) {
			systemsRank.addAll(cacheRecordTemp.data);
		} else {
			cacheRecordTemp = new Record();
		}
		if (cacheRecordTemp.blocks == null) {
			cacheRecordTemp.blocks = divide(cH.getSize(), cH.getCombinationSize() * 2);
		}
		return cacheRecordTemp;
	}

	protected static List<Block> retrieveAssignedBlocks(Properties config, Record cacheRecordTemp) {
		String blockAssignees = config.getProperty("blocks.assegnee");
		List<Block> blocks = new ArrayList<>();
		if (blockAssignees != null) {
			String thisHostName = NetworkUtils.INSTANCE.thisHostName();
			for (String blockAssignee : blockAssignees.split(";")) {
				String[] blockAssigneeInfo = blockAssignee.replaceAll("\\s+","").split(":");
				if (blockAssigneeInfo[0].equalsIgnoreCase(thisHostName)) {
					for (String blockIndex : blockAssigneeInfo[1].split(",")) {
						if (blockIndex.equalsIgnoreCase("odd")) {
							blocks.addAll(CollectionUtils.odd(cacheRecordTemp.blocks));
						} else if (blockIndex.equalsIgnoreCase("even")) {
							blocks.addAll(CollectionUtils.even(cacheRecordTemp.blocks));
						} else if (blockIndex.contains("/")) {
							String[] subListsInfo = blockIndex.split("/");
							List<List<Block>> subList =
								CollectionUtils.toSubLists((List<Block>)cacheRecordTemp.blocks,
									Double.valueOf(Math.ceil(((List<Block>)cacheRecordTemp.blocks).size() / Double.valueOf(subListsInfo[1]))).intValue()
								);
							blocks.addAll(subList.get(Integer.valueOf(subListsInfo[0]) - 1));
						} else {
							blocks.add(cacheRecordTemp.getBlock(Integer.valueOf(blockIndex) - 1));
						}
					}
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
		return blocks;
	}

	protected static TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> buildDataCollection() {
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> bestSystems = new TreeSet<>((itemOne, itemTwo) -> {
			if (itemOne != itemTwo) {
				for(Number type : Premium.allTypesReversed()) {
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
		Record record
	) {
		LogUtils.info("\nBlocks (size: " + record.blocks.size() + ") status:");
		LogUtils.info(
			"\t" + String.join(
				"\n\t",
				record.blocks.stream().map(Object::toString).collect(Collectors.toList())
			)
		);
		LogUtils.info("Rank (size: " + record.data.size() + "):");
		LogUtils.info(
			"\t" + String.join(
				"\n\t",
				record.data.stream().map(entry ->
					ComboHandler.toString(entry.getKey(), ", ") + ": " + Premium.toString(entry.getValue(), "=", ", ")
					//ComboHandler.toString(entry.getKey(), ", ") + ": " + entry.getValue().toString().replace("{", "").replace("}", "")
				).collect(Collectors.toList())
			) + "\n"
		);
	}

	protected static void store(
		String basePath,
		String cacheKey,
		Record record,
		Record cacheRecord
	) {
		cacheRecord.data = new ArrayList<>(record.data);
		IOUtils.INSTANCE.store(cacheKey, cacheRecord, basePath);
	}


	private static void store(
		String basePath,
		String cacheKey,
		IterationData iterationData,
		TreeSet<Entry<List<Integer>, Map<Number, Integer>>> systemsRank,
		Record toBeCached,
		Block currentBlock,
		int rankSize
	) {
		Record cacheRecord = IOUtils.INSTANCE.load(cacheKey, basePath);
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
				}
			}
		}
		while (systemsRank.size() > rankSize) {
			systemsRank.pollLast();
		}
		toBeCached.data = new ArrayList<>(systemsRank);
		IOUtils.INSTANCE.store(cacheKey, toBeCached, basePath);
		IOUtils.INSTANCE.writeToJSONPrettyFormat(new File(basePath + "/" + cacheKey + ".json"), toBeCached);
	}

	public static List<Block> divide(BigInteger size, long blockNumber) {
		BigInteger blockSize = size.divide(BigInteger.valueOf(blockNumber));
		BigInteger remainedSize = size.mod(BigInteger.valueOf(blockNumber));
		List<Block> blocks = new ArrayList<>();
		BigInteger blockStart = BigInteger.ONE;
		for (int i = 0; i < blockNumber; i++) {
			BigInteger blockEnd = blockStart.add(blockSize.subtract(BigInteger.ONE));
			blocks.add(new Block(blockStart, blockEnd, null));
			blockStart = blockEnd.add(BigInteger.ONE);
		}
		if (remainedSize.compareTo(BigInteger.ZERO) != 0) {
			blocks.add(new Block(blockStart, blockStart.add(remainedSize.subtract(BigInteger.ONE)), null));
		}
		return blocks;
	}

	public static class Record implements Serializable {

		private static final long serialVersionUID = -5223969149097163659L;

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

		public Block(BigInteger start, BigInteger end, BigInteger counter) {
			this.start = start;
			this.end = end;
			this.counter = counter;
		}

		@Override
		public String toString() {
			return "Block [start=" + MathUtils.INSTANCE.format(start) + ", end=" + MathUtils.INSTANCE.format(end) + ", counter=" + MathUtils.INSTANCE.format(counter) + "]";
		}

	}

}
