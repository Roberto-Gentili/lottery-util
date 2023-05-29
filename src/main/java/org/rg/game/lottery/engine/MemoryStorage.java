package org.rg.game.lottery.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.rg.game.core.LogUtils;

public class MemoryStorage implements Storage {

	List<List<Integer>> combos = new ArrayList<>();
	String output;
	String name;
	boolean isClosed;
	Map<Integer, Integer> occurrences;

	MemoryStorage(
		LocalDate extractionDate,
		int combinationCount,
		int numberOfCombos,
		String group,
		String suffix
	) {
		name = "[" + extractionDate.toString() + "]"+"[" + combinationCount +"]" +
				"[" + numberOfCombos + "]" + /*"[" + toRawString(numbers) + "]" +*/ suffix + ".txt";
		combos = new ArrayList<>();
		output = "";
		occurrences = new LinkedHashMap<>();
	}

	@Override
	public int size() {
		return combos.size();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<Integer> getCombo(int idx) {
		return combos.get(idx);
	}

	@Override
	public boolean addCombo(List<Integer> combo) {
		if (!contains(combo)) {
			output += ComboHandler.toString(combo) + "\n";
			for (Integer number : combo) {
				Integer counter = occurrences.computeIfAbsent(number, key -> 0) + 1;
				occurrences.put(number, counter);
			}
			return combos.add(combo);
		}
		return false;
	}

	public boolean contains(List<Integer> selectedCombo) {
		for (List<Integer> combo : combos) {
			for (Integer number : combo) {
				if (!selectedCombo.contains(number)) {
					return false;
				}
			}
		}
		return !combos.isEmpty();
	}

	@Override
	public void addUnindexedCombo(List<Integer> selectedCombo) {
		addLine(ComboHandler.toString(selectedCombo));
	}

	@Override
	public void printAll() {
		LogUtils.info(output);
	}

	@Override
	public boolean addLine(String value) {
		output += "\n" + value;
		return true;
	}

	@Override
	public boolean addLine() {
		output += "\n";
		return true;
	}

	@Override
	public void delete() {
		combos.clear();
		output = "";
	}

	@Override
	public Iterator<List<Integer>> iterator() {
		return new Iterator<List<Integer>>() {
			int currentIndex = 0;
			@Override
			public List<Integer> next() {
				return combos.get(currentIndex++);
			}

			@Override
			public boolean hasNext() {
				try {
					combos.get(currentIndex);
					return true;
				} catch (IndexOutOfBoundsException exc) {
					return false;
				}
			}
		};
	}

	@Override
	public void close() {
		isClosed = true;
	}

	@Override
	public boolean isClosed() {
		return isClosed;
	}

	@Override
	public Integer getMinOccurence() {
		TreeSet<Integer> occurrencesCounter = new TreeSet<>(occurrences.values());
		if (occurrencesCounter.isEmpty()) {
			return 0;
		}
		return occurrencesCounter.iterator().next();
	}

	@Override
	public Integer getMaxOccurence() {
		TreeSet<Integer> occurrencesCounter = new TreeSet<>(occurrences.values());
		if (occurrencesCounter.isEmpty()) {
			return 0;
		}
		return occurrencesCounter.descendingIterator().next();
	}

}
