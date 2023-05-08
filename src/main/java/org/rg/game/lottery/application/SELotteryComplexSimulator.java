package org.rg.game.lottery.application;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.util.ZipSecureFile;
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
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-complex-simulation", "properties",
				PersistentStorage.buildWorkingPath(),
				ResourceUtils.INSTANCE.getResource("simulations").getAbsolutePath()
			)
		)) {
			List<LocalDate> extractionDates = new ArrayList<>(new SELotteryMatrixGeneratorEngine().computeExtractionDates(config.getProperty("simulation.dates")));
			LocalDate nextAfterLatest = removeNextOfLatestExtractionDate(config, extractionDates);
			if (!extractionDates.isEmpty()) {
				Random random = new Random(allTimeStats.getSeedData(extractionDates.stream().findFirst().orElseGet(() -> null)).getValue());
				Iterator<Integer> randomIntsIterator = random.ints(0, simpleConfigurations.size()).boxed().iterator();
				Map<Properties, Set<LocalDate>> extractionDatesForConfigs = new LinkedHashMap<>();
				for (int i = 0; i < extractionDates.size(); i++) {
					Properties simpleConfiguration = simpleConfigurations.get(randomIntsIterator.next());
					Set<LocalDate> extractionDatesForConfig=
							extractionDatesForConfigs.computeIfAbsent(simpleConfiguration, key -> new TreeSet<>());
					extractionDatesForConfig.add(extractionDates.get(i));
				}
				for (Map.Entry<Properties, Set<LocalDate>> extractionDatesForConfig : extractionDatesForConfigs.entrySet()) {
					extractionDatesForConfig.getKey().setProperty(
						"simulation.dates",
						String.join(
							",",
							extractionDatesForConfig.getValue().stream().map(TimeUtils.defaultLocalDateFormat::format).collect(Collectors.toList())
						)
					);
					extractionDatesForConfig.getKey().setProperty("simulation.group", config.getProperty("simulation.group"));
				}
				prepareAndProcess(futures, SELotteryMatrixGeneratorEngine::new, simpleConfigurations);
				if (nextAfterLatest != null) {
					Properties nextAfterLatestConfiguration = new Properties();
					nextAfterLatestConfiguration.putAll(simpleConfigurations.get(randomIntsIterator.next()));
					nextAfterLatestConfiguration.put("competition", TimeUtils.defaultLocalDateFormat.format(nextAfterLatest));
					nextAfterLatestConfiguration.setProperty("storage", "filesystem");
					nextAfterLatestConfiguration.setProperty("overwrite-if-exists", "1");
					String simulationGroup = nextAfterLatestConfiguration.getProperty("simulation.group");
					if (simulationGroup != null) {
						nextAfterLatestConfiguration.setProperty("simulation.group", simulationGroup + File.separator + "generated");
					}
					setGroup(nextAfterLatestConfiguration);
					LotteryMatrixGenerator.process(futures, SELotteryMatrixGeneratorEngine::new, nextAfterLatestConfiguration);
				}
			}
		}

	}

}
