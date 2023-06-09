package org.rg.game.lottery.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Premium {
	public static final Integer TYPE_TWO = 2;
	public static final Integer TYPE_THREE = 3;
	public static final Integer TYPE_FOUR = 4;
	public static final Integer TYPE_FIVE = 5;
	public static final Double TYPE_FIVE_PLUS = 5.5d;
	public static final Integer TYPE_SIX = 6;


	public static final String LABEL_TWO = "Ambo";
	public static final String LABEL_THREE = "Terno";
	public static final String LABEL_FOUR = "Quaterna";
	public static final String LABEL_FIVE = "Cinquina";
	public static final String LABEL_FIVE_PLUS = "Cinquina + Jolly";
	public static final String LABEL_SIX = "Tombola";

	private static final Map<Number, String> all;
	private static final List<String> allLabels;
	static {
		all = new LinkedHashMap<>();
		all.put(TYPE_TWO, LABEL_TWO);
		all.put(TYPE_THREE, LABEL_THREE);
		all.put(TYPE_FOUR, LABEL_FOUR);
		all.put(TYPE_FIVE, LABEL_FIVE);
		all.put(TYPE_FIVE_PLUS, LABEL_FIVE_PLUS);
		all.put(TYPE_SIX, LABEL_SIX);
		allLabels = new ArrayList<>(all.values());
	}

	public static List<String> allLabels() {
		return allLabels;
	}

	public static Map<Number, String> all() {
		return all;
	}

	public static String toLabel(Number hit) {
		String label = all.entrySet().stream()
			.filter(entry -> entry.getKey().doubleValue() == hit.doubleValue())
			.map(Map.Entry::getValue).findFirst().orElseGet(() -> null);
		if (label != null) {
			return label;
		}
		throw new IllegalArgumentException("Unvalid premium type: " + hit);
	}

	public static Number parseType(String typeAsString) {
		Double type = Double.valueOf(typeAsString);
		if (type.compareTo(TYPE_FIVE_PLUS) == 0) {
			return TYPE_FIVE_PLUS;
		}
		return Integer.parseInt(typeAsString);
	}

	public static Number toType(String label) {
		for (Entry<Number, String> entry : all.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(label)) {
				return entry.getKey();
			}
		}
		throw new IllegalArgumentException("Unvalid premium label: " + label);
	}

}
