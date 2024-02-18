package org.rg.game.lottery.engine;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.rg.game.core.MathUtils;

public class ComboHandlerTest {

	public static class ComboHandlerEnhanced extends ComboHandler {

		public ComboHandlerEnhanced(List<Integer> numbers, long combinationSize) {
			super(numbers, combinationSize);
		}

		protected List<Integer> computeComboFromCounter(BigInteger counter) {
			int[] indexes = new int[(int)combinationSize];
			int numbersCount = numbers.size();
			BigInteger diff = getSize().subtract(counter);
			//diff = (a!/(6!*((a-6)!)))+(b!/(5!*((b-5)!)))+(c!/(4!*((c-4)!)))+(d!/(3!*((d-3)!)))+(e!/(2!*((e-2)!)))+(f!/(1!*((f-1)!)))
			for (int i = 0; i < indexes.length; i++) {
				diff = diff.multiply(
					MathUtils.INSTANCE.factorial(
						combinationSize - i
					).multiply(
						MathUtils.INSTANCE.factorial(numbersCount - combinationSize - i)
					)
				);
				indexes[i] = numbersCount - MathUtils.Factorial.inverse_factorial(
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
		//87 86	85 83
		//88 87 86 84
		//int[] combo = {1, 2, 3, 4};
		List<Integer> combo = Arrays.asList(1, 25, 80, 82, 84, 86);
		//int[] combo = {1, 26, 56, 75};
		ComboHandlerEnhanced sECmbh = new ComboHandlerEnhanced(SEStats.NUMBERS, combo.size());
		BigInteger index = sECmbh.computeCounter(combo);
		sECmbh.iterate(iterationData -> {
			if (iterationData.getCounter().longValue() == index.longValue()) {
				System.out.println(
					iterationData.getCombo().stream()
		            .map(Object::toString)
		            .collect(Collectors.joining(", "))
		        );
			}
		});
		combo = sECmbh.computeComboFromCounter(index);
		ComboHandler comboHandler = new ComboHandler(SEStats.NUMBERS, 4);
		org.rg.game.lottery.engine.old.ComboHandler oldComboHandler = new org.rg.game.lottery.engine.old.ComboHandler(SEStats.NUMBERS, 4);
		LocalDate now = LocalDate.now();
		long startTime = System.currentTimeMillis();
		PersistentStorage storageForOld = new PersistentStorage(
			LocalDate.now(),
			(int) comboHandler.getCombinationSize(),
			comboHandler.getSizeAsInt(), "test", "testWithOld"
		);
		List<CompletableFuture<Void>> tasks = new ArrayList<>();
		tasks.add(
			CompletableFuture.runAsync(() -> {
				oldComboHandler.iterate(iterationData -> {
					storageForOld.addCombo(iterationData.getCombo());
				});
			})
		);
		PersistentStorage storageForNew = new PersistentStorage(
			LocalDate.now(),
			(int) comboHandler.getCombinationSize(),
			comboHandler.getSizeAsInt(), "test", "testWithNew"
		);
		tasks.add(
			CompletableFuture.runAsync(() -> {
				comboHandler.iterate(iterationData -> {
					storageForNew.addCombo(iterationData.getCombo());
					if (iterationData.getCounter().longValue() % 1000000 == 0 || iterationData.isLatest()) {
						System.out.println(iterationData.getCounter());
						System.out.println(
							iterationData.getCombo().stream()
				            .map(Object::toString)
				            .collect(Collectors.joining(", "))
				        );
					}
				});
			})
		);
		tasks.stream().forEach(CompletableFuture::join);
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
