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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
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
		Collection<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
		execute("se", futures);
		futures.stream().forEach(future -> {
			if (future != null) {
				future.join();
			}
		});
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
				Shared.pathsFromSystemEnv(
					"working-path.complex-simulations.folder",
					"resources.complex-simulations.folder"
				)
			)
		)) {
			List<LocalDate> extractionDates = new ArrayList<>(new SELotteryMatrixGeneratorEngine().computeExtractionDates(config.getProperty("simulation.dates")));
			LocalDate nextAfterLatest = removeNextOfLatestExtractionDate(config, extractionDates);
			if (!extractionDates.isEmpty()) {

				Supplier<Integer> configurationIndexIterator = indexIterator(config, extractionDates, simpleConfigurations);
				Map<Properties, Set<LocalDate>> extractionDatesForConfigs = new LinkedHashMap<>();
				for (int i = 0; i < extractionDates.size(); i++) {
					Properties simpleConfiguration = simpleConfigurations.get(configurationIndexIterator.get());
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
					extractionDatesForConfig.getKey().setProperty(
						"simulation.slave",
						config.getProperty("simulation.slave", extractionDatesForConfig.getKey().getProperty("simulation.slave"))
					);
					extractionDatesForConfig.getKey().setProperty(
						"waiting-someone-for-generation.timeout",
						config.getProperty("waiting-someone-for-generation.timeout", extractionDatesForConfig.getKey().getProperty("waiting-someone-for-generation.timeout"))
					);
				}
				prepareAndProcess(futures, SELotteryMatrixGeneratorEngine::new, simpleConfigurations);
				if (nextAfterLatest != null) {
					Properties nextAfterLatestConfiguration = new Properties();
					nextAfterLatestConfiguration.putAll(simpleConfigurations.get(configurationIndexIterator.get()));
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
			backup(
				new File(
					PersistentStorage.buildWorkingPath() + File.separator + retrieveExcelFileName(config)
				),
				Boolean.parseBoolean(config.getProperty("simulation.slave"))
			);
		}

	}

	protected static Supplier<Integer> indexIterator(Properties configuration, List<LocalDate> extractionDates, List<Properties> simpleConfigurations) {
		String simulationsOrder = configuration.getProperty("simulation.order", "random");
		if (simulationsOrder.startsWith("random")) {
			IntSupplier stepSupplier = buildStepSupplier(extractionDates, simulationsOrder);
			Supplier<Integer> randomItr = randomIterator(extractionDates, 0, simpleConfigurations.size())::next;
			AtomicInteger indexWrapper = new AtomicInteger(randomItr.get());
			AtomicInteger latestStepValue = new AtomicInteger(stepSupplier.getAsInt());
			return () -> {
				if (latestStepValue.decrementAndGet() == 0) {
					latestStepValue.set(stepSupplier.getAsInt());
					return indexWrapper.getAndSet(randomItr.get());
				}
				return indexWrapper.get();
			};
		} else if (simulationsOrder.startsWith("sequence")) {
			IntSupplier stepSupplier = buildStepSupplier(extractionDates, simulationsOrder);
			AtomicInteger indexWrapper = new AtomicInteger(0);
			AtomicInteger latestStepValue = new AtomicInteger(stepSupplier.getAsInt());
			return () -> {
				if (indexWrapper.get() == simpleConfigurations.size()) {
					indexWrapper.set(0);
				}
				if (latestStepValue.decrementAndGet() == 0) {
					latestStepValue.set(stepSupplier.getAsInt());
					return indexWrapper.getAndIncrement();
				} else {
					return indexWrapper.get();
				}
			};
		}
		throw new IllegalArgumentException("Unvalid simulation.order parameter value");
	}

	protected static IntSupplier buildStepSupplier(List<LocalDate> extractionDates, String rawOptions) {
		String[] options = rawOptions.split(":");
		return options.length > 1?
			options[1].contains("random") ?
				boundedRandomizer(extractionDates, options[1].split("random")[1].replaceAll("\\s+","").split("->")) :
				() -> Integer.parseInt(options[1]) :
					() -> 1;
	}

	private static IntSupplier boundedRandomizer(List<LocalDate> extractionDates, String[] minAndMax) {
		if (minAndMax.length != 2) {
			minAndMax = new String[] {"1", minAndMax[0]};
		}
		Random random = new Random(allTimeStats.getSeedData(extractionDates.stream().findFirst().orElseGet(() -> null)).getValue());
		Iterator<Integer> randomIterator = random.ints(Integer.parseInt(minAndMax[0]), Integer.parseInt(minAndMax[1]) + 1).boxed().iterator();
		return randomIterator::next;
	}

	protected static Iterator<Integer> randomIterator(List<LocalDate> extractionDates, int minValue, int maxValue) {
		Random random = new Random(allTimeStats.getSeedData(extractionDates.stream().findFirst().orElseGet(() -> null)).getValue());
		return random.ints(minValue, maxValue).boxed().iterator();
	}

}
