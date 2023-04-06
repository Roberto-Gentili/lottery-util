package org.rg.game.lottery.engine;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
		return parse(filterAsString, false);
	}

	public Predicate<List<Integer>> parse(String filterAsString, boolean logFalseResults) {
		if (filterAsString == null || (filterAsString = filterAsString.replaceAll("\\s+","")).isEmpty()) {
			return numbers -> true;
		}
		Map<String, String> nestedExpression = new LinkedHashMap<>();
		Predicate<List<Integer>> predicate = parseComplexExpression(filterAsString, nestedExpression, logFalseResults);
		return combo -> {
			Collections.sort(combo);
			return predicate.test(combo);
		};
	}

	//"\\((.|\\n)*\\)"
	private Predicate<List<Integer>> parseComplexExpression(String filterAsString, Map<String, String> nestedExpression, boolean logFalseResults) {
		Matcher matcher = Pattern.compile("(.*?)(&|\\||\\/)").matcher(filterAsString + "/");
		Predicate<List<Integer>> predicate = null;
		String logicalOperator = null;
		while (matcher.find()) {
			if (predicate == null) {
				predicate = parseSimpleExpression(matcher.group(1), logFalseResults);
			} else if ("&".equals(logicalOperator)) {
				predicate = predicate.and(parseSimpleExpression(matcher.group(1), logFalseResults));
			} else if ("|".equals(logicalOperator)) {
				predicate = predicate.or(parseSimpleExpression(matcher.group(1), logFalseResults));
			}
			logicalOperator = matcher.group(2);
		}
		return predicate;
	}

	private Predicate<List<Integer>> parseSimpleExpression(String filterAsString, boolean logFalseResults) {
		if (filterAsString.contains("emainder")) {
			return buildPredicate(filterAsString, this::buildRemainderFilter, logFalseResults);
		} else if (filterAsString.contains("sameLastDigit")) {
			return buildPredicate(filterAsString, this::buildSameLastDigitFilter, logFalseResults);
		} else if (filterAsString.contains("consecutiveLastDigit")) {
			return buildPredicate(filterAsString, this::buildConsecutiveLastDigitFilter, logFalseResults);
		} else if (filterAsString.contains("consecutiveNumber")) {
			return buildPredicate(filterAsString, this::buildConsecutiveNumberFilter, logFalseResults);
		} else if (filterAsString.contains("radius")) {
			return buildPredicate(filterAsString, this::buildRadiusFilter, logFalseResults);
		} else if (filterAsString.contains("sum")) {
			return buildPredicate(filterAsString, this::buildSumFilter, logFalseResults);
		} else if (filterAsString.contains("in")) {
			return buildPredicate(filterAsString, this::inFilter, logFalseResults);
		} else if (filterAsString.contains("->")) {
			return buildPredicate(filterAsString, this::buildNumberGroupFilter, logFalseResults);
		}
		return null;
	}

	private Predicate<List<Integer>> inFilter(
		String filterAsString
	) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("in");
		String[] options = operationOptions[1].split(":");
		List<Integer> numbers = Arrays.stream(options[0].split(",")).map(Integer::parseInt).collect(Collectors.toList());
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int counter = 0;
			for (Integer number : combo) {
				if (numbers.indexOf(number) > -1) {
					counter++;
				}
			}
			return counter >= bounds[0] && counter <= bounds[1];
		};
	}


	private Predicate<List<Integer>> buildSumFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("sum");
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int sum = combo.stream().collect(Collectors.summingInt(Integer::intValue)).intValue();
			return sum >= bounds[0] && sum <= bounds[1];
		};
	}

	private Predicate<List<Integer>> buildRadiusFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("radius");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int leftOffset = Integer.parseInt(options[0].split(",")[0]);
		int rightOffset = Integer.parseInt(options[0].split(",")[1]);
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int maxNumbersInRange = 0;
			for (Integer number : combo) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						return true;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				int numbersInRangeCounter = 0;
				int leftBound = number + leftOffset;
				int rightBound = number + rightOffset;
				for (Integer innNumber : combo) {
					if (number != innNumber && innNumber >= leftBound && innNumber <= rightBound) {
						if (numbersInRangeCounter == 0) {
							numbersInRangeCounter++;
						}
						if (++numbersInRangeCounter > bounds[1]) {
							return false;
						} else if (numbersInRangeCounter > maxNumbersInRange) {
							maxNumbersInRange = numbersInRangeCounter;
						}
					}
				}
				if (rangeOptions.length > 1 && numbersInRangeCounter < bounds[0]) {
					return false;
				}
			}
			return rangeOptions.length > 1 || maxNumbersInRange >= bounds[0];
		};
	}

	private Predicate<List<Integer>> buildConsecutiveNumberFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("consecutiveNumber");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int counter = 0;
			int maxConsecutiveNumberCounter = 0;
			Integer previousNumber = null;
			for (int number : combo) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						break;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				if (previousNumber != null && ((number != 0 && previousNumber == number -1) || (number == 0 && previousNumber == 9))) {
					if (counter == 0) {
						counter++;
					}
					if (++counter > maxConsecutiveNumberCounter) {
						maxConsecutiveNumberCounter = counter;
					}
				} else {
					counter = 0;
				}
				previousNumber = number;
			}
			return maxConsecutiveNumberCounter >= bounds[0] && maxConsecutiveNumberCounter <= bounds[1];
		};
	}

	private Predicate<List<Integer>> buildNumberGroupFilter(String filterAsString) {
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
	}

	private Predicate<List<Integer>> buildRemainderFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("noRemainder|remainder");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ?
			Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ?
			Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		double divisor = options[0].isEmpty() ? 2 : Double.parseDouble(options[0]);
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		DoublePredicate evenOrOddTester = filterAsString.contains("noRemainder") ?
			number -> number % divisor == 0 :
			number -> number % divisor != 0;
		return combo -> {
			int evenOrOddCounter = 0;
			for (Integer number : combo) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						break;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				if(evenOrOddTester.test(number) && ++evenOrOddCounter > bounds[1]) {
					return false;
				}
			}
			return evenOrOddCounter >= bounds[0];
		};
	}

	private Predicate<List<Integer>> buildSameLastDigitFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("sameLastDigit");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			int[] counters = new int[10];
			for (Integer number : combo) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						break;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				counters[(number % 10)]++;
			}
			int maxSameDigitCount = Arrays.stream(counters).summaryStatistics().getMax();
			return maxSameDigitCount >= bounds[0] && maxSameDigitCount <= bounds[1];
		};
	}

	private Predicate<List<Integer>> buildConsecutiveLastDigitFilter(String filterAsString) {
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("consecutiveLastDigit");
		String[] rangeOptions = operationOptions[0].split("->");
		Integer leftRangeBounds = rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[0]) : null;
		Integer rightRangeBounds =  rangeOptions.length > 1 ? Integer.parseInt(rangeOptions[1]) : null;
		String[] options = operationOptions[1].split(":");
		String[] boundsAsString = options[1].split(",");
		int[] bounds = {
			Integer.parseInt(boundsAsString[0]),
			Integer.parseInt(boundsAsString[1])
		};
		return combo -> {
			Set<Integer> lastDigits = new TreeSet<>();
			for (Integer number : combo) {
				if (rangeOptions.length > 1) {
					if (number > rightRangeBounds) {
						break;
					} else if (number < leftRangeBounds) {
						continue;
					}
				}
				lastDigits.add(number % 10);
			}
			if (lastDigits.size() >= bounds[0] && lastDigits.size() <= bounds[1]) {
				return true;
			}
			int counter = 0;
			int maxConsecutiveLastDigitCounter = 0;
			Integer previousNumber = null;
			for (int number : lastDigits) {
				if (previousNumber != null && ((number != 0 && previousNumber == number -1) || (number == 0 && previousNumber == 9))) {
					if (counter == 0) {
						counter++;
					}
					if (++counter > maxConsecutiveLastDigitCounter) {
						maxConsecutiveLastDigitCounter = counter;
					}
				} else {
					counter = 0;
				}
				previousNumber = number;
			}
			return maxConsecutiveLastDigitCounter >= bounds[0] && maxConsecutiveLastDigitCounter <= bounds[1];
		};
	}

	private Predicate<List<Integer>> buildPredicate(
		String filterAsString, Function<String, Predicate<List<Integer>>> predicateBuilder, boolean logFalseResults
	) {
		Predicate<List<Integer>> predicate = predicateBuilder.apply(filterAsString);
		if (logFalseResults) {
			return combo -> {
				boolean result = predicate.test(combo);
				if (!result) {
					System.out.println("[" + filterAsString + "] returned false on combo:\t" + toString(combo));
				}
				return result;
			};
		}
		return predicate;
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
