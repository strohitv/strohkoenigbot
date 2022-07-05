package tv.strohi.twitch.strohkoenigbot.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimezoneUtils {
	private TimezoneUtils() {
	}

	public static boolean timeOfTimezoneIsBetweenTimes(String timezone, int startHour, int startMinute, int endHour, int endMinute) {
		if (timezone != null && !timezone.isBlank()) {
			ZonedDateTime time = Instant.now().atZone(ZoneId.of(timezone));
			return time.getHour() >= startHour &&time.getHour() <= endHour && time.getMinute() >= startMinute && time.getMinute() <= endMinute;
		}

		return false;
	}
}
