package org.rg.game.lottery.engine;

import java.util.ArrayList;
import java.util.List;

public class MemoryStorage implements Storage {

	List<List<Integer>> combos = new ArrayList<>();
	String output;
	List<Integer> numbers;
	MemoryStorage(List<Integer> numbers) {
		combos = new ArrayList<>();
		this.numbers = numbers;
		output = "";
	}

	@Override
	public int size() {
		return combos.size();
	}

	@Override
	public List<Integer> getCombo(int idx) {
		return combos.get(idx);
	}

	@Override
	public boolean addCombo(List<Integer> selectedCombo) {
		if (!contains(selectedCombo)) {
			output += "\n" + toString(selectedCombo);
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
		addLine(toString(selectedCombo));
	}

	@Override
	public void printAll() {
		System.out.println("Il sistema e' composto da " + numbers.size() + " numeri: " + toSimpleString(numbers) + "\n" + output);
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

}
