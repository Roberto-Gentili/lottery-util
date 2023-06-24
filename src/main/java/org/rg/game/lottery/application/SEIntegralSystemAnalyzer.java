package org.rg.game.lottery.application;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.rg.game.core.IOUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.MathUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.ComboHandler;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.Premium;
import org.rg.game.lottery.engine.SEPremium;
import org.rg.game.lottery.engine.SEStats;

class SEIntegralSystemAnalyzer {

	public static void main(String[] args) {
		ComboHandler cH = new ComboHandler(IntStream.range(1, 91).boxed().collect(Collectors.toList()), 8);
		AtomicReference<BigInteger> processedSystemsCounterWrapper = new AtomicReference<>(BigInteger.valueOf(0));
		BigInteger modder = BigInteger.valueOf(10_000_000);
		SEStats sEStats = SEStats.get("02/07/2009", "23/06/2023");
		Collection<List<Integer>> allWinningCombos = sEStats.getAllWinningCombosWithJollyAndSuperstar().values();
		LogUtils.info("All systems size: " +  MathUtils.INSTANCE.format(cH.getSize()));
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
		String basePath = PersistentStorage.buildWorkingPath("Analizzatore sistemi integrali");
		String cacheKey = "[" + MathUtils.INSTANCE.format(cH.getSize()).replace(".", "_") + "][" + cH.getCombinationSize() + "]" +
				TimeUtils.getDefaultDateFmtForFilePrefix().format(sEStats.getStartDate()) +
				TimeUtils.getDefaultDateFmtForFilePrefix().format(sEStats.getEndDate());
		Map.Entry<AtomicReference<BigInteger>, Collection<Map.Entry<List<Integer>, Map<Number, Integer>>>> dataFromCache =
			IOUtils.INSTANCE.load(cacheKey, basePath);
		AtomicReference<BigInteger> cachedProcessedSystemsCounterWrapper = new AtomicReference<>(null);
		if (dataFromCache != null) {
			cachedProcessedSystemsCounterWrapper.set(dataFromCache.getKey().get());
		}
		Map.Entry<AtomicReference<BigInteger>, TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>>> data = new AbstractMap.SimpleEntry<>(processedSystemsCounterWrapper, bestSystems);
		if (dataFromCache != null) {
			data.getValue().addAll(dataFromCache.getValue());
		}
		AtomicReference<Map.Entry<AtomicReference<BigInteger>, Collection<Map.Entry<List<Integer>, Map<Number, Integer>>>>> dataFromCacheWrapper =
			new AtomicReference<>(
				new AbstractMap.SimpleEntry<>(processedSystemsCounterWrapper, null)
			);
		cH.iterate(combo -> {
			processedSystemsCounterWrapper.set(processedSystemsCounterWrapper.get().add(BigInteger.ONE));
			BigInteger cachedIndex = cachedProcessedSystemsCounterWrapper.get();
			if (cachedIndex != null) {
				if (cachedIndex.compareTo(processedSystemsCounterWrapper.get()) == 0) {
					cachedProcessedSystemsCounterWrapper.set(null);
					LogUtils.info(
						"Cache succesfully restored, starting from index " + MathUtils.INSTANCE.format(processedSystemsCounterWrapper.get()) + ". " +
						MathUtils.INSTANCE.format(cH.getSize().subtract(processedSystemsCounterWrapper.get())) + " systems remained."
					);
					return;
				}
				return;
			}
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
				boolean addedItemFlag = bestSystems.add(addedItem);
				if (bestSystems.size() > 100) {
					Map.Entry<List<Integer>, Map<Number, Integer>> removedItem = bestSystems.pollLast();
					if (removedItem != addedItem) {
						store(basePath, cacheKey, data, dataFromCacheWrapper, combo, allPremiums);
						LogUtils.info(
							"Replacing data from rank:\n\t" + ComboHandler.toString(removedItem.getKey(), ", ") + ": " + removedItem.getValue() +
							"\n\t\twith"+
							ComboHandler.toString(combo, ", ") + ": " + allPremiums
						);
					}
				} else if (addedItemFlag) {
					store(basePath, cacheKey, data, dataFromCacheWrapper, combo, allPremiums);
					LogUtils.info("Adding data to rank: " + ComboHandler.toString(combo, ", ") + ": " + allPremiums);
				}
			}
			if (processedSystemsCounterWrapper.get().mod(modder).compareTo(BigInteger.ZERO) == 0) {
				store(basePath, cacheKey, data, dataFromCacheWrapper, combo, allPremiums);
	    		LogUtils.info("Storing rank data: ");
    		}
			/*Map<>
			for (Integer number : combo) {
				if () {

				}
			}
			allWinningCombos.get

			if (counter.get().mod(modder).compareTo(BigInteger.ZERO) == 0) {
	    		LogUtils.info("Tested " + counter.get() + " of combinations");
    		}
			sEStats.*/
		});
		LogUtils.info(processedSystemsCounterWrapper.get() + " of combinations analyzed");
	}

	protected static void store(String basePath, String cacheKey,
		Map.Entry<AtomicReference<BigInteger>, TreeSet<Map.Entry<List<Integer>, Map<Number, Integer>>>> data,
		AtomicReference<Map.Entry<AtomicReference<BigInteger>,
		Collection<Map.Entry<List<Integer>, Map<Number, Integer>>>>> dataFromCacheWrapper,
		List<Integer> combo,
		Map<Number, Integer> allPremiums
	) {
		Collection<Map.Entry<List<Integer>, Map<Number, Integer>>> comboAndPremiumDataToBeCached = new ArrayList<>();
		dataFromCacheWrapper.get().setValue(comboAndPremiumDataToBeCached);
		comboAndPremiumDataToBeCached.addAll(data.getValue());
		IOUtils.INSTANCE.store(cacheKey, (Serializable)dataFromCacheWrapper.get(), basePath);	}

}
