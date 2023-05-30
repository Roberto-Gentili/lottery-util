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

import org.rg.game.core.CollectionUtils;
import org.rg.game.core.LogUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.core.Throwables;
import org.rg.game.core.TimeUtils;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.SEStats;



public class SELotteryComplexSimulator extends SELotterySimpleSimulator {


	public static void main(String[] args) throws IOException {
		Collection<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
		executeRecursive(SELotteryComplexSimulator::execute, futures);
	}

	protected static Collection<CompletableFuture<Void>> execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) {
		SEStats.forceLoadingFromExcel = false;
		allTimeStats = SEStats.get("03/12/1997", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.get("02/07/2009", TimeUtils.getDefaultDateFormat().format(new Date()));
		SEStats.forceLoadingFromExcel = true;
		String[] configurationFileFolders = Shared.pathsFromSystemEnv(
			"working-path.complex-simulations.folder",
			"resources.complex-simulations.folder"
		);
		LogUtils.info("Set configuration files folder to " + String.join(", ", configurationFileFolders) + "\n");
		try {
			for (Properties complexSimulationConfig : toConfigurations(
				ResourceUtils.INSTANCE.find(
					configFilePrefix + "-complex-simulation", "properties",
					configurationFileFolders
				),
				"simulation.children.slave"
			)) {
				List<LocalDate> extractionDates = new ArrayList<>(new SELotteryMatrixGeneratorEngine().computeExtractionDates(complexSimulationConfig.getProperty("simulation.children.dates")));
				LocalDate nextAfterLatest = removeNextOfLatestExtractionDate(complexSimulationConfig, extractionDates);
				if (!extractionDates.isEmpty()) {
					String[] childrenSimulationsFilters = complexSimulationConfig.getProperty("simulation.children", "allEnabledInSameFolder").replaceAll("\\s+","").split(",");
					List<File> simpleConfigurationFiles = new ArrayList<>();
					for (String childrenSimulationsFilter : childrenSimulationsFilters) {
						if (childrenSimulationsFilter.equals("allInSameFolder") || childrenSimulationsFilter.equals("allEnabledInSameFolder")) {
							simpleConfigurationFiles.addAll(
								ResourceUtils.INSTANCE.find(
									configFilePrefix + "-simple-simulation", "properties",
									Shared.pathsFromSystemEnv(
										"working-path.complex-simulations.folder",
										"resources.complex-simulations.folder"
									)
								)
							);
							if (childrenSimulationsFilter.equals("allEnabledInSameFolder") || childrenSimulationsFilter.equals("allInSameFolder")) {
								Iterator<File> simpleConfigFileIterator = simpleConfigurationFiles.iterator();
								while (simpleConfigFileIterator.hasNext()) {
									Properties simpleConfig = ResourceUtils.INSTANCE.toProperties(simpleConfigFileIterator.next());
									if (childrenSimulationsFilter.equals("allEnabledInSameFolder") && !CollectionUtils.retrieveBoolean(simpleConfig, "simulation.enabled", "false")) {
										simpleConfigFileIterator.remove();
									} else if (childrenSimulationsFilter.equals("allInSameFolder")) {
										simpleConfig.setProperty("simulation.enabled", "true");
									}
								}
							}
						} else {
							simpleConfigurationFiles.add(ResourceUtils.INSTANCE.toFile(complexSimulationConfig.getProperty("file.parent.absolutePath"), childrenSimulationsFilter));
						}
					}
					List<Properties> simpleConfigurations = ResourceUtils.INSTANCE.toOrderedProperties(simpleConfigurationFiles);
					for (Properties simpleConfiguration : simpleConfigurations) {
						simpleConfiguration.setProperty("simulation.enabled", "true");
					}
					Supplier<Integer> configurationIndexIterator = indexIterator(complexSimulationConfig, extractionDates, simpleConfigurations);
					Map<Properties, Set<LocalDate>> extractionDatesForSimpleConfigs = new LinkedHashMap<>();
					for (int i = 0; i < extractionDates.size(); i++) {
						Properties simpleConfiguration = simpleConfigurations.get(configurationIndexIterator.get());
						Set<LocalDate> extractionDatesForConfig=
								extractionDatesForSimpleConfigs.computeIfAbsent(simpleConfiguration, key -> new TreeSet<>());
						extractionDatesForConfig.add(extractionDates.get(i));
					}
					for (Map.Entry<Properties, Set<LocalDate>> extractionDatesForSimpleConfig : extractionDatesForSimpleConfigs.entrySet()) {
						extractionDatesForSimpleConfig.getKey().setProperty(
							"simulation.dates",
							String.join(
								",",
								extractionDatesForSimpleConfig.getValue().stream().map(TimeUtils.defaultLocalDateFormat::format).collect(Collectors.toList())
							)
						);
						if (complexSimulationConfig.getProperty("simulation.children.group") == null) {
							complexSimulationConfig.setProperty("simulation.children.group", complexSimulationConfig.getProperty("file.name").replace(".properties", ""));
						}
						extractionDatesForSimpleConfig.getKey().setProperty(
							"simulation.group",
							complexSimulationConfig.getProperty(
								"simulation.children.group"
							)
						);
						extractionDatesForSimpleConfig.getKey().setProperty(
							"simulation.slave",
							complexSimulationConfig.getProperty(
								"simulation.children.slave",
								extractionDatesForSimpleConfig.getKey().getProperty("slave")
							)
						);
						extractionDatesForSimpleConfig.getKey().setProperty(
							"waiting-someone-for-generation.timeout",
							complexSimulationConfig.getProperty(
								"simulation.children.waiting-someone-for-generation.timeout",
								extractionDatesForSimpleConfig.getKey().getProperty("waiting-someone-for-generation.timeout")
							)
						);
						extractionDatesForSimpleConfig.getKey().setProperty(
							"async",
							complexSimulationConfig.getProperty(
								"simulation.children.async",
								extractionDatesForSimpleConfig.getKey().getProperty("async")
							)
						);
						if (complexSimulationConfig.getProperty("simulation.children.redundancy") != null) {
							extractionDatesForSimpleConfig.getKey().setProperty(
								"simulation.redundancy",
								complexSimulationConfig.getProperty(
									"simulation.children.redundancy",
									extractionDatesForSimpleConfig.getKey().getProperty("simulation.redundancy")
								)
							);
						}
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
						PersistentStorage.buildWorkingPath() + File.separator + retrieveExcelFileName(complexSimulationConfig, "simulation.children.group")
					),
					CollectionUtils.retrieveBoolean(complexSimulationConfig, "simulation.slave", "false")
				);
			}
		} catch (IOException exc) {
			Throwables.sneakyThrow(exc);
		}
		return futures;
	}

	protected static Supplier<Integer> indexIterator(Properties complexSimulationConfiguration, List<LocalDate> extractionDates, List<Properties> simpleConfigurations) {
		String simulationsOrder = complexSimulationConfiguration.getProperty("simulation.children.order", "sequence");
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
