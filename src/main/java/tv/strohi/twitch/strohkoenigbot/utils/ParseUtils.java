package tv.strohi.twitch.strohkoenigbot.utils;

public class ParseUtils {
	private ParseUtils() {
	}

	public static Long parseLongSafe(String value) {
		return parseLongSafe(value, null);
	}

	public static Long parseLongSafe(String value, Long defaultValue) {
		Long result = defaultValue;

		try {
			result = Long.parseLong(value);
		} catch (Exception ignored) {
		}

		return result;
	}
}
