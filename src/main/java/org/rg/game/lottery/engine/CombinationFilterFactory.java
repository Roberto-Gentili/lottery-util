package org.rg.game.lottery.engine;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CombinationFilterFactory {
	public static final CombinationFilterFactory INSTANCE;

	static {
		INSTANCE = new CombinationFilterFactory();
	}

	protected SimpleDateFormat simpleDateFormatter = new SimpleDateFormat("dd/MM/yyyy");
	protected DecimalFormat decimalFormat = new DecimalFormat( "#,##0.##" );
	protected DecimalFormat integerFormat = new DecimalFormat( "#,##0" );

	private CombinationFilterFactory() {}

	public Predicate<List<Integer>> parse(String filterAsString) {
		if (filterAsString == null || filterAsString.replaceAll("\\s+","").isEmpty()) {
			return numbers -> true;
		}
		return parseComplexExpression(filterAsString);
	}

	private Predicate<List<Integer>> parseComplexExpression(String filterAsString) {
		if (filterAsString.contains("&") || filterAsString.contains("|")) {
			int andCharacterIndex = filterAsString.indexOf("&");
			int orCharacterIndex = filterAsString.indexOf("|");
			if ((andCharacterIndex > -1 && orCharacterIndex > -1) ?
					andCharacterIndex < orCharacterIndex :
					andCharacterIndex > -1) {
				return parseSimpleExpression(filterAsString.substring(0, andCharacterIndex))
					.and(parseComplexExpression(filterAsString.substring(andCharacterIndex +1, filterAsString.length())));
			} else {
				return parseSimpleExpression(filterAsString.substring(0, orCharacterIndex))
					.or(parseComplexExpression(filterAsString.substring(orCharacterIndex +1, filterAsString.length())));
			}
		}
		return parseSimpleExpression(filterAsString);
	}

	private Predicate<List<Integer>> parseSimpleExpression(String filterAsString) {
		if (filterAsString.contains("->")) {
			String[] expressions = filterAsString.split(";");
			int[][] bounds = new int[expressions.length][4];
			boolean allMinAreZeroTemp = true;
			for (int i = 0; i < expressions.length; i++) {
				String[] expression = expressions[i].replaceAll("\\s+","").split(":");
				String[] boundsAsString = expression[0].split("->");
				bounds[i][0] = Integer.parseInt(boundsAsString[0]);
				bounds[i][1] = Integer.parseInt(boundsAsString[1]);
				String[] values = expression[1].split(",");
				if ((bounds[i][2] = Integer.parseInt(values[0])) > 0) {
					allMinAreZeroTemp = false;
				}
				bounds[i][3] = Integer.parseInt(values[1]);
			}
			boolean allMinAreZero =  allMinAreZeroTemp;
			return combo -> {
				int[] checkCounter = new int[bounds.length];
				for (Integer number : combo) {
					for (int i = 0; i < bounds.length; i++) {
						if (number >= bounds[i][0] && number <= bounds[i][1] && ++checkCounter[i] > bounds[i][3]) {
							return false;
						}
					}
				}
				if (!allMinAreZero) {
					for (int i = 0; i < bounds.length; i++) {
						if (checkCounter[i] < bounds[i][2]) {
							return false;
						}
					}
				}
				return true;
			};
		} else if (filterAsString.contains("odd") || filterAsString.contains("even")) {
			String[] boundsAsString = filterAsString.replaceAll("\\s+","").replace("odd", "").replace("even", "").split(":")[1].split(",");
			int[] bounds = {
				Integer.parseInt(boundsAsString[0]),
				Integer.parseInt(boundsAsString[1])
			};
			IntPredicate evenOrOddTester = filterAsString.contains("even") ?
				number -> number % 2 == 0 :
				number -> number % 2 != 0;
			return combo -> {
				int evenOrOddCounter = 0;
				for (Integer number : combo) {
					if(evenOrOddTester.test(number) && ++evenOrOddCounter > bounds[1]) {
						return false;
					}
				}
				return evenOrOddCounter >= bounds[0];
			};
		} else if (filterAsString.contains("sameLastDigit")) {
			String[] boundsAsString = filterAsString.replaceAll("\\s+","").replace("odd", "").replace("even", "").split(":")[1].split(",");
			int[] bounds = {
				Integer.parseInt(boundsAsString[0]),
				Integer.parseInt(boundsAsString[1])
			};
			return combo -> {
				int[] counters = new int[10];
				for (Integer number : combo) {
					counters[(number % 10)]++;
				}
				int maxSameDigitCount = Arrays.stream(counters).summaryStatistics().getMax();
				return maxSameDigitCount >= bounds[0] && maxSameDigitCount <= bounds[1];
			};
		}
		return null;
	}

	String toString(List<Integer> combo) {
		return String.join(
			"\t",
			combo.stream()
		    .map(Object::toString)
		    .collect(Collectors.toList())
		);
	}

}
