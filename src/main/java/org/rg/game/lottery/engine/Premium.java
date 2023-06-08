package org.rg.game.lottery.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Premium {
	public static final Integer TYPE_AMBO = 2;
	public static final Integer TYPE_TERNO = 3;
	public static final Integer TYPE_QUATERNA = 3;
	public static final Integer TYPE_CINQUINA = 5;
	public static final Integer TYPE_TOMBOLA = 6;


	public static final String LABEL_AMBO = "Ambo";
	public static final String LABEL_TERNO = "Terno";
	public static final String LABEL_QUATERNA = "Quaterna";
	public static final String LABEL_CINQUINA = "Cinquina";
	public static final String LABEL_TOMBOLA = "Tombola";

	private static final Map<Integer, String> all;
	private static final List<String> allLabels;
	static {
		all = new LinkedHashMap<>();
		all.put(TYPE_AMBO, LABEL_AMBO);
		all.put(TYPE_TERNO, LABEL_TERNO);
		all.put(TYPE_QUATERNA, LABEL_QUATERNA);
		all.put(TYPE_CINQUINA, LABEL_CINQUINA);
		all.put(TYPE_TOMBOLA, LABEL_TOMBOLA);
		allLabels = new ArrayList<>(all.values());
	}

	public static List<String> allLabels() {
		return allLabels;
	}

	public static Map<Integer, String> all() {
		return all;
	}

	public static String toLabel(Integer hit) {
		String label = all.get(hit);
		if (label != null) {
			return label;
		}
		throw new IllegalArgumentException("Unvalid premium type: " + hit);
	}

	public static Integer toType(String label) {
		for (Entry<Integer, String> entry : all.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(label)) {
				return entry.getKey();
			}
		}
		throw new IllegalArgumentException("Unvalid premium label: " + label);
	}

}
