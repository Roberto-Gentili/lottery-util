package org.rg.game.lottery.application;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.rg.game.core.CollectionUtils;
import org.rg.game.core.NetworkUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;



public class SELotteryComplexSimulator extends SELotterySimpleSimulator {


	public static void main(String[] args) throws IOException {
		hostName = NetworkUtils.INSTANCE.thisHostName();
		ZipSecureFile.setMinInflateRatio(0);
		Collection<CompletableFuture<Void>> futures = new ArrayList<>();
		execute("se", futures);
		futures.stream().forEach(CompletableFuture::join);
	}

	protected static void execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) throws IOException {
		SEStats.forceLoadingFromExcel = false;
		allTimeStats = SEStats.get("03/12/1997", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.get("02/07/2009", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		List<File> simpleConfigurationFiles =
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-simple-simulation", "properties",
				PersistentStorage.buildWorkingPath(),
				ResourceUtils.INSTANCE.getResource("simulations").getAbsolutePath()
			);
		List<Properties> simpleConfigurations = new ArrayList<>();
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(simpleConfigurationFiles)) {
			if (Boolean.parseBoolean(config.getProperty("simulation.enabled", "false"))) {
				simpleConfigurations.add(config);
			}
		}
		Supplier<SELotteryMatrixGeneratorEngine> engineSupplier = SELotteryMatrixGeneratorEngine::new;
		SELotteryMatrixGeneratorEngine engine = engineSupplier.get();
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-complex-simulation", "properties",
				PersistentStorage.buildWorkingPath(),
				ResourceUtils.INSTANCE.getResource("simulations").getAbsolutePath()
			)
		)) {
			List<LocalDate> extractionDates = new ArrayList<>(engine.computeExtractionDates(config.getProperty("simulation.dates")));
			removeNextOfLatestExtractionDate(config, extractionDates);
			if (!extractionDates.isEmpty()) {
				Random random = new Random(TimeUtils.toDate(extractionDates.stream().findFirst().orElseGet(() -> null)).getTime());
				Set<Integer> extractionDateIndexesFlat = new LinkedHashSet<>();
				Iterator<Integer> randomIntsIterator = random.ints(0, extractionDates.size()).boxed().iterator();
				while (extractionDateIndexesFlat.size() < extractionDates.size()) {
					extractionDateIndexesFlat.add(randomIntsIterator.next());
				}
				List<List<Integer>> extractionDateIndexesSplitted =
					CollectionUtils.toSubLists(new ArrayList<>(extractionDateIndexesFlat), simpleConfigurations.size());
				for (int i = 0; i < simpleConfigurations.size(); i++) {
					Properties simpleConfiguration = simpleConfigurations.get(i);
					List<Integer> extractionDatesIndexes = extractionDateIndexesSplitted.get(i);
					Set<LocalDate> selectedExtractionDate = new TreeSet<>();
					for (Integer extractionDateIndex : extractionDatesIndexes) {
						selectedExtractionDate.add(extractionDates.get(extractionDateIndex));
					}
					simpleConfiguration.setProperty(
						"simulation.dates",
						String.join(
							",",
							selectedExtractionDate.stream().map(TimeUtils.defaultLocalDateFormat::format).collect(Collectors.toList())
						)
					);
					simpleConfiguration.setProperty("simulation.group", config.getProperty("simulation.group"));
				}
				prepareAndProcess(futures, engineSupplier, simpleConfigurations);
			}
		}

	}

}
