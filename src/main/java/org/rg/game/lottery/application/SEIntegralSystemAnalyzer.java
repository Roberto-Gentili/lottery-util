package org.rg.game.lottery.application;

import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.rg.game.core.IOUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.MathUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.ComboHandler.IterationData;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SEPremium;
import org.rg.game.lottery.engine.SEStats;

class SEIntegralSystemAnalyzer {

	public static void main(String[] args) {
		ComboHandler cH = new ComboHandler(IntStream.range(1, 91).boxed().collect(Collectors.toList()), 8);
		BigInteger modder = BigInteger.valueOf(10_000_000);
		SEStats sEStats = SEStats.get("02/07/2009", "23/06/2023");
		Collection<List<Integer>> allWinningCombos = sEStats.getAllWinningCombosWithJollyAndSuperstar().values();
		LogUtils.info("All systems size: " +  MathUtils.INSTANCE.format(cH.getSize()));
		String basePath = PersistentStorage.buildWorkingPath("Analizzatore sistemi integrali");
		String cacheKey = "[" + MathUtils.INSTANCE.format(cH.getSize()).replace(".", "_") + "][" + cH.getCombinationSize() + "]" +
				TimeUtils.getDefaultDateFmtForFilePrefix().format(sEStats.getStartDate()) +
				TimeUtils.getDefaultDateFmtForFilePrefix().format(sEStats.getEndDate());
		Record cacheRecordTemp = IOUtils.INSTANCE.load(cacheKey, basePath);
		TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>> systemsRank = buildDataCollection();
		if (cacheRecordTemp != null) {
			systemsRank.addAll(cacheRecordTemp.getData());
		} else {
			cacheRecordTemp = new Record();
		}
		Record cacheRecord = cacheRecordTemp;
		cH.iterate(iterationData -> {
			BigInteger cachedCounter = cacheRecord.getCounter();
			if (cachedCounter != null) {
				if (cachedCounter.compareTo(iterationData.getCounter()) == 0) {
					cacheRecord.setCounter(null);
					LogUtils.info(
						"Cache succesfully restored, starting from index " + MathUtils.INSTANCE.format(iterationData.getCounter()) + ". " +
						MathUtils.INSTANCE.format(cH.getSize().subtract(iterationData.getCounter())) + " systems remained."
					);
					printData(cacheRecord);
					return;
				}
				return;
			}
			List<Integer> combo = iterationData.getCombo();
			Map<Number, Integer> allPremiums = new LinkedHashMap<>();
			for (Number premiumType : Premium.allTypesReversed()) {
				allPremiums.put(premiumType, 0);
			}
			allWinningCombos.stream().forEach(winningComboWithSuperStar -> {
				Map<Number, Integer> premiums = SEPremium.checkIntegral(combo, winningComboWithSuperStar);
				for (Map.Entry<Number, Integer> premiumTypeAndCounter : allPremiums.entrySet()) {
					Number premiumType = premiumTypeAndCounter.getKey();
					Integer premiumCounter = premiums.get(premiumType);
					if (premiumCounter != null) {
						allPremiums.put(premiumType, allPremiums.get(premiumType) + premiumCounter);
					}
				}
			});
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
				if (systemsRank.size() > 100) {
					Map.Entry<List<Integer>, Map<Number, Integer>> removedItem = systemsRank.pollLast();
					if (removedItem != addedItem) {
						store(basePath, cacheKey, iterationData, systemsRank, cacheRecord);
						LogUtils.info(
							"Replacing data from rank:\n\t" + ComboHandler.toString(removedItem.getKey(), ", ") + ": " + removedItem.getValue() + "\n" +
							"\t\twith\n"+
							"\t" + ComboHandler.toString(addedItem.getKey(), ", ") + ": " + addedItem.getValue()
						);
					}
				} else if (addedItemFlag) {
					store(basePath, cacheKey, iterationData, systemsRank, cacheRecord);
					LogUtils.info("Adding data to rank: " + ComboHandler.toString(combo, ", ") + ": " + allPremiums);
				}
			}
			if (iterationData.getCounter().mod(modder).compareTo(BigInteger.ZERO) == 0) {
				LogUtils.info(MathUtils.INSTANCE.format(iterationData.getCounter()) + " of systems have been processed");
				store(basePath, cacheKey, iterationData, systemsRank, cacheRecord);
    		}
		});
		//LogUtils.info(processedSystemsCounterWrapper.get() + " of combinations analyzed");
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
		LogUtils.info("Current data:");
		LogUtils.info(
			String.join(
				"\n",
				record.getData().stream().map(entry -> ComboHandler.toString(entry.getKey(), ", ") + ": " + entry.getValue()).collect(Collectors.toList())
			)
		);
	}

	protected static void store(
		String basePath,
		String cacheKey,
		Record record,
		Record cacheRecord
	) {
		cacheRecord.setCounter(record.getCounter());
		cacheRecord.setData(new ArrayList<>(record.getData()));
		IOUtils.INSTANCE.store(cacheKey, cacheRecord, basePath);
	}


	private static void store(
		String basePath,
		String cacheKey,
		IterationData iterationData,
		Collection<Entry<List<Integer>, Map<Number, Integer>>> systemsRank,
		Record cacheRecord
	) {
		cacheRecord.setCounter(iterationData.getCounter());
		cacheRecord.setData(new ArrayList<>(systemsRank));
		IOUtils.INSTANCE.store(cacheKey, cacheRecord, basePath);
		IOUtils.INSTANCE.writeToJSONPrettyFormat(new File(basePath + "/" + cacheKey + ".json"), cacheRecord);
	}

	public static class Record implements Serializable {

		private static final long serialVersionUID = -5223969149097163659L;

		private BigInteger counter;
		private Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data;

		public BigInteger getCounter() {
			return counter;
		}
		public void setCounter(BigInteger counter) {
			this.counter = counter;
		}
		public Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> getData() {
			return data;
		}
		public void setData(Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> data) {
			this.data = data;
		}

	}

}
