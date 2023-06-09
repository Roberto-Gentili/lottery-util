package org.rg.game.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Comparator;

public class MathUtils {

	public static final MathUtils INSTANCE = new MathUtils();

	public DecimalFormat decimalFormat = new DecimalFormat( "#,##0.##" );
	public DecimalFormat integerFormat = new DecimalFormat( "#,##0" );
	public Comparator<Number> numberComparator = (numberOne, numberTwo) -> {
		double numberOneAsDouble = numberOne.doubleValue();
		double numberTwoAsDouble = numberTwo.doubleValue();
		return numberOneAsDouble > numberTwoAsDouble ? 1 :
			numberOneAsDouble < numberTwoAsDouble ? -1 : 0;
	};

	public DecimalFormat getNewDecimalFormat() {
		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setGroupingSeparator(',');
		symbols.setDecimalSeparator('.');
		DecimalFormat decimalFormat = new DecimalFormat("#.#", symbols);
		decimalFormat.setParseBigDecimal(true);
		return decimalFormat;
	}

	public BigDecimal stringToBigDecimal(String value) {
		return stringToBigDecimal(value, getNewDecimalFormat());
	}

	public BigDecimal stringToBigDecimal(String value, DecimalFormat decimalFormat) {
		value =	value.trim();
		if (value.contains(".")) {
			String wholeNumber = value.substring(0, value.indexOf("."));
			String fractionalPart = value.substring(value.indexOf(".") + 1, value.length());
			fractionalPart = fractionalPart.replace(".", "");
			value = wholeNumber + "." + fractionalPart;
		}
		try {
			return ((BigDecimal)decimalFormat.parse(value));
		} catch (ParseException exc) {
			return Throwables.sneakyThrow(exc);
		}
	}

	public BigInteger factorial(BigInteger number) {
		BigInteger factorial = BigInteger.ONE;
		BigInteger divisor = BigInteger.valueOf(100_000);
		BigInteger initialValue = number;
		while (number.compareTo(BigInteger.ZERO) > 0) {
			factorial = factorial.multiply(number);
			number = number.subtract(BigInteger.ONE);
			BigInteger processedNumbers = initialValue.subtract(number);
			if (processedNumbers.mod(divisor).compareTo(BigInteger.ZERO) == 0) {
				LogUtils.info("Processed " + processedNumbers
					.toString() + " numbers - Factorial: " + factorial.toString());
			}
		}
		return factorial;
	}

	public BigInteger factorial(Number number) {
		return factorial(BigInteger.valueOf(number.longValue()));
	}

}
