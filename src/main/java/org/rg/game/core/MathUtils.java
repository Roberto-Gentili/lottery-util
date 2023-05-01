package org.rg.game.core;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;

public class MathUtils {

	public static final MathUtils INSTANCE = new MathUtils();

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
			throw new RuntimeException(exc);
		}
	}

}
