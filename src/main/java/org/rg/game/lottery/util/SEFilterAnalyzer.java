package org.rg.game.lottery.util;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.rg.game.lottery.engine.SELotteryMatrixGeneratorEngine;

public class SEFilterAnalyzer {
	private static String filter =
		"sameLastDigit: 0,4&" +
		"1 -> 9: 0,3;" +
		"10 -> 19: 0,3;" +
		"20 -> 29: 0,3;" +
		"30 -> 39: 0,3;" +
		"40 -> 49: 0,3;" +
		"50 -> 59: 0,3;" +
		"60 -> 69: 0,3;" +
		"70 -> 79: 0,3;" +
		"80 -> 90: 0,3;";
	public static void main(String[] args) {
		SELotteryMatrixGeneratorEngine engine = new SELotteryMatrixGeneratorEngine();
		engine.testEffectiveness(
			filter,
			IntStream.rangeClosed(1, 90)
		    .boxed().collect(Collectors.toList()),
			true
		);

	}

}
