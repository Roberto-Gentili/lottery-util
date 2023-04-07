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
import java.util.stream.IntStream;

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
		if (filterAsString == null || filterAsString.isEmpty()) {
			return numbers -> true;
		}
		Predicate<List<Integer>> filter = parseComplexExpression(filterAsString.replace("\t", " ").replace("\n", "").replace("\r", ""), logFalseResults);
		return combo -> {
			Collections.sort(combo);
			return filter.test(combo);
		};
	}

	private Predicate<List<Integer>> parseComplexExpression(String expression, boolean logFalseResults) {
		Map<String, Object> nestedExpressionsData = new LinkedHashMap<>();
		expression = bracketAreasToPlaceholders(expression, nestedExpressionsData);
		Matcher logicalOperatorSplitter = Pattern.compile("(.*?)(&|\\||\\/)").matcher(expression + "/");
		Predicate<List<Integer>> predicate = null;
		String logicalOperator = null;
		while (logicalOperatorSplitter.find()) {
			String originalePredicateUnitExpression = logicalOperatorSplitter.group(1);
			String predicateUnitExpression = originalePredicateUnitExpression.startsWith("!") ?
				originalePredicateUnitExpression.split("\\!")[1] :
				originalePredicateUnitExpression;
			String nestedPredicateExpression = (String)nestedExpressionsData.get(predicateUnitExpression);
			Predicate<List<Integer>> predicateUnit = nestedPredicateExpression != null ?
				parseComplexExpression(nestedPredicateExpression, logFalseResults) :
				parseSimpleExpression(predicateUnitExpression, logFalseResults);
			if (originalePredicateUnitExpression.startsWith("!")) {
				predicateUnit = predicateUnit.negate();
			}
			if (predicate == null) {
				predicate = predicateUnit;
			} else if ("&".equals(logicalOperator)) {
				predicate = predicate.and(predicateUnit);
			} else if ("|".equals(logicalOperator)) {
				predicate = predicate.or(predicateUnit);
			}
			logicalOperator = logicalOperatorSplitter.group(2);
		}
		return predicate;
	}

	private Predicate<List<Integer>> parseSimpleExpression(String expression, boolean logFalseResults) {
		Predicate<List<Integer>> filter = null;
		if (expression.contains("emainder")) {
			filter = buildPredicate(expression, this::buildRemainderFilter, logFalseResults);
		} else if (expression.contains("sameLastDigit")) {
			filter = buildPredicate(expression, this::buildSameLastDigitFilter, logFalseResults);
		} else if (expression.contains("consecutiveLastDigit")) {
			filter = buildPredicate(expression, this::buildConsecutiveLastDigitFilter, logFalseResults);
		} else if (expression.contains("consecutiveNumber")) {
			filter = buildPredicate(expression, this::buildConsecutiveNumberFilter, logFalseResults);
		} else if (expression.contains("radius")) {
			filter = buildPredicate(expression, this::buildRadiusFilter, logFalseResults);
		} else if (expression.contains("sum")) {
			filter = buildPredicate(expression, this::buildSumFilter, logFalseResults);
		} else if (expression.contains("in")) {
			filter = buildPredicate(expression, this::inFilter, logFalseResults);
		} else if (expression.contains("->")) {
			filter = buildPredicate(expression, this::buildNumberGroupFilter, logFalseResults);
		} else if (expression.equals("true")) {
			filter = combo -> true;
		} else if (expression.equals("false")) {
			filter = combo -> false;
		}
		if (filter == null) {
			throw new IllegalArgumentException("Unrecognized expression: " + expression);
		}
		return filter;
	}

	static String bracketAreasToPlaceholders(String expression, Map<String, Object> values) {
		String replacedExpression = null;
		while (!expression.equals(replacedExpression = findAndReplaceNextBracketArea(expression, values))) {
			expression = replacedExpression;
		}
		return expression;
	}

	static String findAndReplaceNextBracketArea(String expression, Map<String, Object> values) {
		values.computeIfAbsent("nestedIndex", key -> 0);
		int firstLeftBracketIndex = expression.indexOf("(");
		if (firstLeftBracketIndex > -1) {
			int close = findClose(expression, firstLeftBracketIndex);  // find the  close parentheses
            String bracketInnerArea = expression.substring(firstLeftBracketIndex + 1, close);
            Integer nestedIndex = (Integer)values.get("nestedIndex");
            String placeHolder = "__NESTED-"+ nestedIndex++ +"__";
            expression = expression.substring(0, firstLeftBracketIndex) + placeHolder + expression.substring(close + 1, expression.length());
            values.put("nestedIndex", nestedIndex);
            values.put(placeHolder, bracketInnerArea);
            return expression;
		}
		return expression;
	}

	static int findClose(String input, int start) {
        java.util.Stack<Integer> stack = new java.util.Stack<>();
        for (int index = start; index < input.length(); index++) {
            if (input.charAt(index) == '(') {
                stack.push(index);
            } else if (input.charAt(index) == ')') {
                stack.pop();
                if (stack.isEmpty()) {
                    return index;
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced brackets in expression: " + input);
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
		Set<Integer> sums = new TreeSet<>();
		String[] operationOptions = filterAsString.replaceAll("\\s+","").split("sum");
		for (String sumAsString : operationOptions[1].split(",")) {
			String[] rangeOptions = sumAsString.split("->");
			if (rangeOptions.length > 1) {
				IntStream.rangeClosed(
					Integer.parseInt(rangeOptions[0]),
					Integer.parseInt(rangeOptions[1])).boxed().collect(Collectors.toCollection(() -> sums)
				);
			} else {
				sums.add(Integer.parseInt(sumAsString));
			}
		}
		return combo -> {
			return sums.contains(
				combo.stream().collect(Collectors.summingInt(Integer::intValue)).intValue()
			);
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
					System.out.println("[" + filterAsString + "] returned false on combo:\t" + ComboHandler.toString(combo));
				}
				return result;
			};
		}
		return predicate;
	}

}
