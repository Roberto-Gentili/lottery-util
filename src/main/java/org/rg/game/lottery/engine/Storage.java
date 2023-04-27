package org.rg.game.lottery.engine;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public interface Storage extends AutoCloseable {

	int size();

	public String getName();

	boolean addCombo(List<Integer> selectedCombo);

	List<Integer> getCombo(int idx);

	boolean addLine(String value);

	void addUnindexedCombo(List<Integer> selectedCombo);

	void printAll();

	default String toSimpleString(List<Integer> combo) {
		return String.join(
			",",
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	default String toRawString(List<Integer> combo) {
		return String.join(
			"",
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

	boolean addLine();

	@Override
	default void close() {

	}

	public Iterator<List<Integer>> iterator();

	void delete();
}
