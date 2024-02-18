package org.rg.game.lottery.engine;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.rg.game.core.CollectionUtils;
import org.rg.game.core.MathUtils;

public class ComboHandler {

	protected List<Integer> numbers;
	protected long combinationSize;
	protected BigInteger size;

	public ComboHandler(List<Integer> numbers, long combinationSize) {
		this.numbers = new ArrayList<>(numbers);
		this.combinationSize = combinationSize;
	}

	public BigInteger getSize() {
		if (size == null) {
			size = sizeOf(numbers.size(), combinationSize);
		}
		return size;
	}

	public long getCombinationSize() {
		return combinationSize;
	}

	public static Long sizeOfAsLong(Number numbersCount, Number combinationCount) {
		return sizeOf(BigInteger.valueOf(numbersCount.longValue()), BigInteger.valueOf(combinationCount.longValue())).longValue();
	}

	public static BigInteger sizeOf(Number numbersCount, Number combinationCount) {
		return sizeOf(
			BigInteger.valueOf(numbersCount.longValue()),
			BigInteger.valueOf(combinationCount.longValue())
		);
	}

	public static BigInteger sizeOf(BigInteger numbersCount, BigInteger combinationCount) {
		return MathUtils.INSTANCE.factorial(
			numbersCount
		).divide(
			MathUtils.INSTANCE.factorial(combinationCount)
			.multiply(
				MathUtils.INSTANCE.factorial(numbersCount.subtract(combinationCount))
			)
		);
	}

	public int getSizeAsInt() {
		return getSize().intValue();
	}

	public long getSizeAsLong() {
		return getSize().longValue();
	}

	public List<Integer> getNumbers() {
		return numbers;
	}

	public Map<Long, List<Integer>> find(Collection<Long> indexes, boolean useSameCollectionInstance) {
		Map<Long, List<Integer>> result = new HashMap<>();
		if (indexes.isEmpty()) {
			return result;
		}
		Collection<Long> indexesToBeFound = useSameCollectionInstance ? indexes : new HashSet<>(indexes);
		iterate(iterationData -> {
			Long currentIndex = iterationData.getCounter().subtract(BigInteger.ONE).longValue();
			if (indexesToBeFound.remove(currentIndex)) {
				result.put(
    				currentIndex,
    				Arrays.stream(iterationData.indexes)
					.map(numbers::get)
					.boxed()
				    .collect(Collectors.toList())
	    		);
				if (indexes.isEmpty()) {
					iterationData.terminateIteration();
				}
	    	}
		});
		if (!indexesToBeFound.isEmpty()) {
			throw new NoSuchElementException("Not all indexes have been found");
		}
		return result;
	}

	private int[] toIndexes(List<Integer> combo) {
		int[] indexes = new int[(int)combinationSize];
		for (int i = 0; i < combo.size(); i++) {
			indexes[i] = numbers.indexOf(Integer.valueOf(combo.get(i)));
		}
		return indexes;
	}

	public BigInteger computeCounter(List<Integer> combo) {
		int[] indexes = toIndexes(combo);
		return computeCounterFromIndexes(indexes);
	}

	protected BigInteger computeCounterFromIndexes(int[] indexes) {
		BigInteger counter = getSize();
		int numbersSize = numbers.size();
		for (int i = 0; i < indexes.length; i++) {
			counter = counter.subtract(
				ComboHandler.sizeOf(
					BigInteger.valueOf(numbersSize - (indexes[i] + 1)),
					combinationSize - i
				)
			);
		}
		return counter;
	}


	public void iterateFrom(
		IterationData iterationData,
		Consumer<IterationData> action
	) {
		try {
			int endIndex = numbers.size() - 1;
			while ((iterationData.setIndexes(nextIndexes(iterationData.indexes, endIndex)) != null)) {
				action.accept(iterationData);
			}
		} catch (TerminateIteration exc) {

		}
	}

	public void iterateFrom(
		List<Integer> combo,
		Consumer<IterationData> action
	) {
		try {
			int[] indexes = toIndexes(combo);
			IterationData iterationData = new IterationData(indexes, computeCounterFromIndexes(indexes));
			int endIndex = numbers.size() - 1;
			while ((iterationData.setIndexes(nextIndexes(iterationData.indexes, endIndex)) != null)) {
				action.accept(iterationData);
			}
		} catch (TerminateIteration exc) {

		}
	}

	public void iterate(
		Consumer<IterationData> action
	) {
		iterateFrom(new IterationData(), action);
	}

	protected int[] nextIndexes(int[] indexes, int endIndex) {
		try {
			for (int i = indexes.length - 1; i >= 0; i--) {
				if (indexes[i] < (endIndex - ((indexes.length - 1) -i))) {
					++indexes[i];
					for (int j = i + 1; j < indexes.length;j++) {
						indexes[j] = indexes[j - 1] + 1;
					}
					return indexes;
				}
			}
			return null;
		} catch (NullPointerException exc) {
			indexes = new int[(int)combinationSize];
			for (int i = 0;i < indexes.length;i++) {
				indexes[i] = i;
			}
			return indexes;
		}
	}

	public static String toExpression(Collection<Integer> numbers) {
		String expression = "";
		Integer previousNumber = null;
		List<Integer> comboSumList = new ArrayList<>(new TreeSet<>(numbers));
		for (int i = 0; i < comboSumList.size(); i++) {
			Integer sum = comboSumList.get(i);
			if (previousNumber == null) {
				expression += sum;
				previousNumber = sum;
			} else if (previousNumber == sum - 1) {
				if (!expression.endsWith("->")) {
					expression += "->";
				}
				if (i < comboSumList.size() - 1) {
					previousNumber = sum;
				} else {
					expression += sum;
				}
			} else if (expression.endsWith("->")) {
				expression += previousNumber + "," + sum;
				previousNumber = sum;
			} else {
				expression += "," +sum;
				previousNumber = sum;
			}
		}
		return expression;
	}

	public static String toString(List<Integer> combo) {
		return toString(combo, "\t");
	}

	public static String toString(List<Integer> combo, String separator) {
		return String.join(
			separator,
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	public static List<Integer> fromString(String values) {
		return Stream.of(values.replaceAll("\\s+","").split(",")).map(Integer::valueOf).collect(Collectors.toCollection(ArrayList::new));
	}

	public static int sumValues(List<Integer> combo) {
		return combo.stream().collect(Collectors.summingInt(Integer::intValue)).intValue();
	}

	public static int sumMultiplicationOfEachValueByItselfReduced(List<Integer> combo, Integer reduction) {
		return processAndSum(combo, number -> number * (number - reduction));
	}

	public static int sumPowerOfValues(List<Integer> combo, Integer exponent) {
		return processAndSum(combo, number -> (int)Math.pow(number, exponent));
	}

	public static int processAndSum(List<Integer> combo, UnaryOperator<Integer> numberProcessor) {
		return combo.stream().map(numberProcessor).collect(Collectors.toList())
		.stream().collect(Collectors.summingInt(Integer::intValue)).intValue();
	}

	public class IterationData implements Serializable {

		private static final long serialVersionUID = 1135569763057593292L;

		private int indexes[];
		private BigInteger counter;
		private transient List<Integer> combo;

		public IterationData() {
			counter = BigInteger.ZERO;
		}

		public IterationData(int indexes[], BigInteger counter) {
			this.indexes = indexes;
			this.counter = counter != null ? counter :BigInteger.ZERO;
		}

		public int[] setIndexes(int[] indexes) {
			this.combo = null;
			this.indexes = indexes;
			counter = counter.add(BigInteger.ONE);
			return this.indexes;
		}

		public BigInteger getCounter() {
			return counter;
		}

		public List<Integer> getCombo() {
			if (combo != null) {
				return combo;
			}
			combo = new ArrayList<>();
	    	for (int numberIndex : indexes) {
	    		combo.add(numbers.get(numberIndex));
	    	}
	    	return combo;
		}

		public List<Integer> getNumbers() {
			return numbers;
		}

		public int[] copyOfIndexes() {
			return CollectionUtils.INSTANCE.copyOf(indexes);
		}

		public <T> T terminateIteration() {
			throw TerminateIteration.NOTIFICATION;
		}

		public boolean isLatest() {
			return counter.compareTo(getSize()) == 0;
		}
	}

}