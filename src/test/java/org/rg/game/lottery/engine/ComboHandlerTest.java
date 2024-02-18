package org.rg.game.lottery.engine;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.rg.game.core.MathUtils;

public class ComboHandlerTest {

	public static class ComboHandlerEnhanced extends ComboHandler {

		public ComboHandlerEnhanced(List<Integer> numbers, long combinationSize) {
			super(numbers, combinationSize);
		}
		@Override
		protected BigInteger computeCounterFromIndexes(int[] indexes) {
			int[] startIndexes = new int[(int)getCombinationSize()];
			int endIndex = getNumbers().size() - 1;
			for (int i = 0;i < startIndexes.length;i++) {
				startIndexes[i] = i;
			}
			BigInteger counter = BigInteger.ZERO;
			if (indexes.length == 2) {
				/*counter = counter.add(
					BigInteger.valueOf(
						MathUtils.INSTANCE.sumOfNaturalNumbersBetween(
							endIndex - indexes[indexes.length - 2],
							endIndex
						)
					)
				);
				return counter.subtract(
					BigInteger.valueOf(
						endIndex - (indexes[indexes.length - 2])
					)
				).add(
					BigInteger.valueOf(indexes[indexes.length - 1] - indexes[indexes.length - 2])
				);*/
				counter = counter.add(
					BigInteger.valueOf(
						MathUtils.INSTANCE.sumOfNaturalNumbersBetween(
							endIndex - (indexes[indexes.length - 2] -1),
							endIndex
						)
					)
				);
				return counter = counter.add(
					BigInteger.valueOf(
						indexes[indexes.length - 1] - indexes[indexes.length - 2]
					)
				);
			} else if (indexes.length == 3) {
//				for (int i = 0; i < indexes[indexes.length - 3]; i++) {
//					counter = counter.add(
//						BigInteger.valueOf(
//							MathUtils.INSTANCE.sumOfNaturalNumbersBetween(
//								startIndexes[startIndexes.length - 2],
//								endIndex - (i + 1)
//							)
//						)
//					);
//				}
				counter = counter.add(
					BigInteger.valueOf(
						computeIncrementation(
							1,
							endIndex - indexes[indexes.length - 3],
							endIndex - 1
						)
					)
				).add(
					BigInteger.valueOf(
						MathUtils.INSTANCE.sumOfNaturalNumbersBetween(
							endIndex - (indexes[indexes.length - 2] - 1),
							endIndex - (indexes[indexes.length - 3] + 1)
						)
					)
				).add(
					BigInteger.valueOf(
						indexes[indexes.length - 1] - indexes[indexes.length - 2]
					)
				);
//				BigInteger.valueOf(
//					MathUtils.INSTANCE.sumOfNaturalNumbersBetween(startIndexes[1], maxIndexesElementValue - 1)
//				).add(
//					BigInteger.valueOf(
//						MathUtils.INSTANCE.sumOfNaturalNumbersBetween(startIndexes[1], maxIndexesElementValue - 2)
//				)
//				).add(
//						BigInteger.valueOf(
//								MathUtils.INSTANCE.sumOfNaturalNumbersBetween(startIndexes[1], maxIndexesElementValue - 3)
//					)).add(
//							BigInteger.valueOf(
//									MathUtils.INSTANCE.sumOfNaturalNumbersBetween(maxIndexesElementValue - (indexes[1] - 1), maxIndexesElementValue - 4) +
//									indexes[2] - indexes[1]
//						));
//				MathUtils.INSTANCE.sumOfNaturalNumbersBetween(maxIndexesElementValue - (indexes[1] - 1), maxIndexesElementValue - 1) +
//				indexes[2] - indexes[1]
			} else if (indexes.length == 4) {
				for (int i = 0; i < indexes[indexes.length - 3]; i++) {
					counter = counter.add(
						BigInteger.valueOf(
							MathUtils.INSTANCE.sumOfNaturalNumbersBetween(
								1,
								endIndex - (i + startIndexes[startIndexes.length - 2])
							)
						)
					);
				}
				counter = BigInteger.ZERO;
				return counter.add(
					BigInteger.valueOf(
						indexes[indexes.length - 1] - indexes[indexes.length - 2]
					)
				);
			}
			return counter;
//			return BigInteger.valueOf(
//					MathUtils.INSTANCE.sumOfNaturalNumbersBetween(
//							startIndexes[startIndexes.length - 3],
//							87
//						)
//					);
		}
	}

	public static void main(String[] args) {
		//87 86	85 83
		//88 87 86 84
		//int[] combo = {1, 2, 3, 4};
		int[] combo = {1, 26, 56, 75};
		ComboHandler SECmbh = new ComboHandlerEnhanced(SEStats.NUMBERS, combo.length);
		BigInteger index = SECmbh.computeCounter(combo);
		SECmbh.iterate(iterationData -> {
			if (iterationData.getCounter().longValue() == index.longValue()) {
				System.out.println(
					iterationData.getCombo().stream()
		            .map(Object::toString)
		            .collect(Collectors.joining(", "))
		        );
			}
		});
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
