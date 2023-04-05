package org.rg.game.lottery.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.io.FileSystemItem;
import org.rg.game.lottery.engine.LotteryMatrixGeneratorAbstEngine;
import org.rg.game.lottery.engine.MDLotteryMatrixGeneratorEngine;
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
		Collection<FileSystemItem> configurationFiles = new TreeSet<>((fISOne, fISTwo) -> {
			return fISOne.getName().compareTo(fISTwo.getName());
		});
		configurationFiles.addAll(
			ComponentContainer.getInstance().getPathHelper().findResources(absolutePath -> {
				return absolutePath.contains(configFilePrefix + "-matrix-generator");
			})
		);
		for (FileSystemItem fIS : configurationFiles) {
			try (InputStream configIS = fIS.toInputStream()) {
				Properties config = new Properties();
				config.load(configIS);
				if (Boolean.parseBoolean(config.getProperty("enabled", "false"))) {
					System.out.println("Processing file " + fIS.getName());
					LotteryMatrixGeneratorAbstEngine engine = engineSupplier.get();
					config.setProperty("nameSuffix", fIS.getName().replace("." + fIS.getExtension(), ""));
					engine.setup(config);
					if (Boolean.parseBoolean(config.getProperty("async", "false"))) {
						futures.add(CompletableFuture.runAsync(() -> engine.getExecutor().run()));
					} else {
						engine.getExecutor().run();
					}
				}
			}
		}
	}

}
