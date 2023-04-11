package org.rg.game.lottery.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionToPredicateEngine<I> {

	protected Map<Predicate<String>, Function<String, Function<Object[], String>>> simpleExpressionsPreprocessors;
	protected Map<Predicate<String>, Function<String, Function<Object[], Predicate<I>>>> simpleExpressionsParsers;


	public ExpressionToPredicateEngine() {
		simpleExpressionsPreprocessors = new LinkedHashMap<>();
		simpleExpressionsParsers = new LinkedHashMap<>();
	}

	public void addSimpleExpressionParser(
		Predicate<String> simpleExpressionsParserPredicate,
		Function<String, Function<Object[], Predicate<I>>> parser
	) {
		simpleExpressionsParsers.put(simpleExpressionsParserPredicate, parser);
	}

	public void addSimpleExpressionPreprocessor(
		Predicate<String> simpleExpressionsParserPredicate,
		Function<String, Function<Object[], String>> preprocessor
	) {
		simpleExpressionsPreprocessors.put(simpleExpressionsParserPredicate, preprocessor);
	}

	public Predicate<I> parse(String expression, Object... additionalParamters) {
		return parseComplexExpression(
			preProcess(
				expression,
				additionalParamters
			),
			additionalParamters
		);
	}

	public String preProcess(String expression, Object... additionalParamters) {
		if (expression == null) {
			return expression;
		}
		Map<String, Object> nestedExpressionsData = new LinkedHashMap<>();
		expression = bracketAreasToPlaceholders(expression, nestedExpressionsData);
		Matcher logicalOperatorSplitter = Pattern.compile("(.*?)(&|\\||\\/)").matcher(expression + "/");
		while (logicalOperatorSplitter.find()) {
			String originalPredicateUnitExpression = logicalOperatorSplitter.group(1);
			String predicateUnitExpression = originalPredicateUnitExpression.startsWith("!") ?
				logicalOperatorSplitter.group(1).split("\\!")[1] :
				originalPredicateUnitExpression;
			String nestedPredicateExpression = (String)nestedExpressionsData.get(predicateUnitExpression);
			String newExpression = nestedPredicateExpression != null ? preProcess(nestedPredicateExpression, additionalParamters) :
				preProcessSimpleExpression(predicateUnitExpression, additionalParamters);
			if (nestedPredicateExpression != null) {
				expression = expression.replace(
					originalPredicateUnitExpression,
					((originalPredicateUnitExpression.startsWith("!") ? "!" : "") + "(" + newExpression + ")"
				));
			} else {
				expression = expression.replace(
					originalPredicateUnitExpression,
					((originalPredicateUnitExpression.startsWith("!") ? "!" : "") + newExpression
				));
			}
		}
		return expression;
	}

	public Predicate<I> parseComplexExpression(String expression, Object... additionalParamters) {
		Map<String, Object> nestedExpressionsData = new LinkedHashMap<>();
		expression = bracketAreasToPlaceholders(expression, nestedExpressionsData);
		Matcher logicalOperatorSplitter = Pattern.compile("(.*?)(&|\\||\\/)").matcher(expression + "/");
		Predicate<I> predicate = null;
		String logicalOperator = null;
		while (logicalOperatorSplitter.find()) {
			String originalePredicateUnitExpression = logicalOperatorSplitter.group(1);
			String predicateUnitExpression = originalePredicateUnitExpression.startsWith("!") ?
				originalePredicateUnitExpression.split("\\!")[1] :
				originalePredicateUnitExpression;
			String nestedPredicateExpression = (String)nestedExpressionsData.get(predicateUnitExpression);
			Predicate<I> predicateUnit = nestedPredicateExpression != null ?
				parseComplexExpression(nestedPredicateExpression, additionalParamters) :
				parseSimpleExpression(predicateUnitExpression, additionalParamters);
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


	protected Predicate<I> parseSimpleExpression(String expression, Object... additionalParamters) {
		for (Entry<Predicate<String>, Function<String, Function<Object[], Predicate<I>>>> expressionToPredicateEntry : simpleExpressionsParsers.entrySet()) {
			if (expressionToPredicateEntry.getKey().test(expression)) {
				return expressionToPredicateEntry.getValue().apply(expression).apply(additionalParamters);
			}
		}
		throw new IllegalArgumentException("Unrecognized expression: " + expression);
	}

	private String preProcessSimpleExpression(String expression, Object... additionalParamters) {
		for (Entry<Predicate<String>, Function<String, Function<Object[], String>>> expressionToPredicateEntry : simpleExpressionsPreprocessors.entrySet()) {
			if (expressionToPredicateEntry.getKey().test(expression)) {
				return expressionToPredicateEntry.getValue().apply(expression).apply(additionalParamters);
			}
		}
		return expression;
	}

}
