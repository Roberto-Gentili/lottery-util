package org.rg.game.lottery.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.rg.game.core.LogUtils;

public class MemoryStorage implements Storage {

	List<List<Integer>> combos = new ArrayList<>();
	String output;
	String name;
	boolean isClosed;

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
	public boolean addCombo(List<Integer> selectedCombo) {
		if (!contains(selectedCombo)) {
			output += ComboHandler.toString(selectedCombo) + "\n";
			return combos.add(selectedCombo);
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

}
