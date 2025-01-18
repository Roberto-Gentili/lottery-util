package org.rg.game.lottery.engine;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.rg.game.core.MathUtils;
import org.rg.game.lottery.application.Shared;

public class ComboHandlerTest {

	public static class ComboHandlerEnhanced extends ComboHandler {

		public ComboHandlerEnhanced(List<Integer> numbers, long combinationSize) {
			super(numbers, combinationSize);
		}

		protected List<Integer> computeComboFromCounter(BigInteger counter) {
			ComboHandler comboHandler = new ComboHandler(SEStats.NUMBERS, 6);
			BigInteger position = comboHandler.computeCounter(
				Arrays.asList(40,51,53,56,68,72)
			);
			List<Integer> combo = comboHandler.computeCombo(position);
			System.out.println(toString(combo));

			int[] indexes = new int[(int)combinationSize];
			int numbersCount = numbers.size();
			BigInteger diff = getSize().subtract(counter);
			//diff = ((90 - a)!/(6!*(((90 - a)-6)!)))+((90 - b)!/(5!*(((90 - b)-5)!)))+((90 - c)!/(4!*(((90 - c)-4)!)))+((90 - d)!/(3!*(((90 - d)-3)!)))+((90 - e)!/(2!*(((90 - e)-2)!)))+((90 - f)!/(1!*(((90 - f)-1)!)))
			//diff = (a!/(6!*((a-6)!)))+(b!/(5!*((b-5)!)))+(c!/(4!*((c-4)!)))+(d!/(3!*((d-3)!)))+(e!/(2!*((e-2)!)))+(f!/(1!*((f-1)!)))

			//620938055
			for (int i = 0; i < indexes.length; i++) {
				diff = diff.multiply(
					MathUtils.INSTANCE.factorial(
						combinationSize - i
					).multiply(
						MathUtils.INSTANCE.factorial(numbersCount - combinationSize - i)
					)
				);
				indexes[i] = numbersCount - MathUtils.Factorial.inverse(
					diff
				).intValue();
//				counter = counter.subtract(
//					ComboHandler.sizeOf(
//						BigInteger.valueOf(numbersSize - (indexes[i] + 1)),
//						combinationSize - i
//					)
//				);
			}
			return Arrays.stream(indexes).boxed().collect(Collectors.toList());
		}

	}

	public static void main(String[] args) {
		ComboHandler comboHandler = new ComboHandler(SEStats.NUMBERS, 6);
		int divider = 1_000_000;
		boolean hasRest = divider*(comboHandler.getSizeAsInt()/divider) != comboHandler.getSizeAsInt();
		int[] comboForGroup = {20, 5};
		int blockSize = (comboHandler.getSizeAsInt()/(comboHandler.getSizeAsInt()/divider)) + (hasRest ? 1 : 0);
		int latestBlockIndex = comboHandler.getSizeAsInt() / blockSize;
		int latestBlockSize = hasRest ? comboHandler.getSizeAsInt() - (latestBlockIndex * blockSize) : blockSize;
		Map<Integer, Integer> winningGroupIndexes = new LinkedHashMap<>();//I gruppi sono memorizzati a partire dallo 0
		SEStats sEStats = Shared.getSEStatsForLatestExtractionDate();
		for (Map.Entry<Date, List<Integer>> extractionData : sEStats.getAllWinningCombosReversed().entrySet()) {
			Integer comboIndex = comboHandler.computeCounter(extractionData.getValue()).intValue();
//			System.out.println(
//				TimeUtils.getDefaultDateFormat().format(extractionData.getKey()) + ": " +
//				index
//			);
			Integer key = comboIndex/blockSize;
			Integer counter = winningGroupIndexes.get(key);
			if (counter == null) {
				winningGroupIndexes.put(key, 1);
			} else {
				winningGroupIndexes.put(key, ++counter);
			}

		}
		winningGroupIndexes = sortByValue(winningGroupIndexes);
		List<Integer> indexes = winningGroupIndexes.keySet().stream().collect(Collectors.toList());
		Collections.reverse(indexes);
		for (Integer index : indexes) {
			System.out.println(index + ": " + winningGroupIndexes.get(index));
		}
		LocalDate nextExtractionDate = SELotteryMatrixGeneratorEngine.DEFAULT_INSTANCE.computeNextExtractionDate(LocalDate.now(), false);
		Random randomizer = new Random(
			Shared.getSEAllStats().getSeedData(
				nextExtractionDate
			).getValue()
		);
		TreeMap<Integer, List<Integer>> combos = new TreeMap<>();
		for (int i = 0; i < comboForGroup[0]; i++) {
			int blockIndex = indexes.get(i);
			int randomBound = blockIndex < latestBlockIndex? blockSize : latestBlockSize;
			int counter = (randomizer.nextInt(randomBound) + 1) + (indexes.get(i) * blockSize);
			List<Integer> combo = comboHandler.computeCombo(BigInteger.valueOf(counter));
			/*System.out.println(
				"From block " +
					(blockIndex + 1) +
					" (" + ((indexes.get(i) * blockSize) + 1) + " -> " +
					((indexes.get(i) * blockSize) + randomBound) + "):\t" +
					counter + "\t->\t" +
				comboHandler.toString(combo)
			);*/
			combos.put(counter, combo);
		}

		System.out.println("Per il concorso numero " + (Shared.getSEAllStats().getAllWinningCombos().size() + 1) +
			" del " + nextExtractionDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) + " il sistema è composto da " + combos.size() + " combinazioni");
		for (Entry<Integer, List<Integer>> row : combos.entrySet()) {
			System.out.println(ComboHandler.toString(row.getValue()));
		}

		List<Integer> randomBlocks = new ArrayList<>();
		for (int i = 0; i < comboForGroup[1]; i++) {
			randomBlocks.add(randomizer.nextInt(latestBlockIndex + 1));
		}
		combos.clear();
		for (Integer blockIndex : randomBlocks) {
			int randomBound = blockIndex < latestBlockIndex? blockSize : latestBlockSize;
			int counter = (randomizer.nextInt(randomBound) + 1) + (indexes.get(blockIndex) * blockSize);
			List<Integer> combo = comboHandler.computeCombo(BigInteger.valueOf(counter));
			/*System.out.println(
				"From block " +
					(blockIndex + 1) +
					" (" + ((indexes.get(i) * blockSize) + 1) + " -> " +
					((indexes.get(i) * blockSize) + randomBound) + "):\t" +
					counter + "\t->\t" +
				comboHandler.toString(combo)
			);*/
			combos.put(counter, combo);
		}
		System.out.println();
		for (Entry<Integer, List<Integer>> row : combos.entrySet()) {
			System.out.println(ComboHandler.toString(row.getValue()));
		}

		//87 86	85 83
		//88 87 86 84
		//int[] combo = {1, 2, 3, 4};
		List<Integer> combo = Arrays.asList(25, 36, 45, 54, 63, 72);
		//int[] combo = {1, 26, 56, 75};
		ComboHandler sECmbh = new ComboHandler(SEStats.NUMBERS, combo.size());
		BigInteger index = sECmbh.computeCounter(combo);

		System.out.println();

		List<Integer> cmb = sECmbh.computeCombo(index);
		sECmbh.iterate(iterationData -> {
			if (iterationData.getCounter().longValue() == index.longValue()) {
				System.out.println(
					iterationData.getCombo().stream()
		            .map(Object::toString)
		            .collect(Collectors.joining(", "))
		        );
			}
		});
	}

	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

	public static double computeIncrementation(double y, double n, double m) {
		return
			((m - n + 1)*
			(Math.pow(m, 2) + (m * (n + 2)) +
			Math.pow(n, 2) + n - ((3 *(y - 1)) * y)))/6;
	}

	public static int computeIncrementation(int y, int m, int n) {
		return (int)computeIncrementation((double)y, (double)m, (double)n);
	}
}
