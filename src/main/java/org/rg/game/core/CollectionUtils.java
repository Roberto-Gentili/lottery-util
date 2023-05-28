package org.rg.game.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CollectionUtils {

	public static <T> List<List<T>> toSubLists(List<T> inputList, int size) {
        final AtomicInteger counter = new AtomicInteger(0);
        List<List<T>> subLists = new ArrayList<>(inputList.stream()
                    .collect(Collectors.groupingBy((s -> counter.getAndIncrement()/size), LinkedHashMap::new, Collectors.toList()))
                    .values());
        return subLists;
	}

	public static boolean retrieveBoolean(Properties config, String key) {
		return retrieveBoolean(config, key, null);
	}

	public static boolean retrieveBoolean(Properties config, String key, String defaultValue) {
		return Boolean.parseBoolean((defaultValue != null ? config.getProperty(key, defaultValue) : config.getProperty(key)).toLowerCase().replaceAll("\\s+",""));
	}

}
