package org.rg.game.lottery.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

}
