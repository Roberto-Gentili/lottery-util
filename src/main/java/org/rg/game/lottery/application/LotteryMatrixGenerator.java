package org.rg.game.lottery.application;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.rg.game.core.LogUtils;
import org.rg.game.core.ResourceUtils;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.MDLotteryMatrixGeneratorEngine;
import org.rg.game.lottery.engine.PersistentStorage;
import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;



public class LotteryMatrixGenerator {

	public static void main(String[] args) throws IOException {
		Collection<CompletableFuture<Void>> futures = new ArrayList<>();
		execute("se", futures);
		execute("md", futures);
		futures.stream().forEach(CompletableFuture::join);
	}

	private static <L extends LotteryMatrixGeneratorAbstEngine> void execute(
		String configFilePrefix,
		Collection<CompletableFuture<Void>> futures
	) throws IOException {
		Supplier<LotteryMatrixGeneratorAbstEngine> engineSupplier =
			configFilePrefix.equals("se") ? SELotteryMatrixGeneratorEngine::new :
				configFilePrefix.equals("md") ? MDLotteryMatrixGeneratorEngine::new : null;

		List<File> configurationFiles =
			ResourceUtils.INSTANCE.find(
				configFilePrefix + "-matrix-generator", "properties",
				PersistentStorage.buildWorkingPath()
			);
		List<Properties> configurations = new ArrayList<>();
		for (Properties config : ResourceUtils.INSTANCE.toOrderedProperties(configurationFiles)) {
			String simulationDates = config.getProperty("simulation.dates");
			if (Boolean.parseBoolean(config.getProperty("enabled", "false"))) {
				configurations.add(config);
			}
		}
		for (Properties configuration : configurations) {
			LogUtils.info(
				"Processing file '" + configuration.getProperty("file.name") + "' located in '" + configuration.getProperty("file.parent.absolutePath") + "'"
			);
			String info = configuration.getProperty("info");
			if (info != null) {
				LogUtils.info(info);
			}
			LotteryMatrixGeneratorAbstEngine engine = engineSupplier.get();
			configuration.setProperty("nameSuffix", configuration.getProperty("file.name")
				.replace("." + configuration.getProperty("file.extension"), ""));
			engine.setup(configuration);
			if (Boolean.parseBoolean(configuration.getProperty("async", "false"))) {
				futures.add(CompletableFuture.runAsync(() -> engine.getExecutor().apply(null).apply(null)));
			} else {
				engine.getExecutor().apply(null).apply(null);
			}
		}
	}

}
