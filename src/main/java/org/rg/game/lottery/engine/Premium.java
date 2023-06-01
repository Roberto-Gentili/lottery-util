package org.rg.game.lottery.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Premium {
	public static final Integer TYPE_TOMBOLA = 6;
	public static final Integer TYPE_CINQUINA = 5;

	private static final Map<Integer, String> all;
	private static final List<String> allLabels;
	static {
		all = new LinkedHashMap<>();
		all.put(2, "Ambo");
		all.put(3, "Terno");
		all.put(4, "Quaterna");
		all.put(TYPE_CINQUINA, "Cinquina");
		all.put(TYPE_TOMBOLA, "Tombola");
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
