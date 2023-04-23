package org.rg.game.lottery.engine;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class TimeUtils {

	public static final String DEFAULT_TIME_ZONE = "Europe/Rome";
	public static SimpleDateFormat defaultDateFormat = new SimpleDateFormat("dd/MM/yyyy");
	public static DateTimeFormatter defaultLocalDateFormatter = DateTimeFormatter.ofPattern(defaultDateFormat.toPattern());

	public static Date toDate(LocalDate date) {
		return Date.from(date.atStartOfDay(ZoneId.of(TimeUtils.DEFAULT_TIME_ZONE)).toInstant());
	}

	public static LocalDate toLocalDate(Date date) {
		return date.toInstant().atZone(ZoneId.of(TimeUtils.DEFAULT_TIME_ZONE)).toLocalDate();
	}

}
