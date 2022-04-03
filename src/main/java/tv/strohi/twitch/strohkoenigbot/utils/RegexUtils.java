package tv.strohi.twitch.strohkoenigbot.utils;

public class RegexUtils {
	private static final String escapeChars = "\\.?![]{}()<>*+-=^$|";
	public static String escapeQuotes(String str) {
		if(str != null && str.length() > 0) {
			return str.replaceAll("[\\W]", "\\\\$0"); // \W designates non-word characters
		}
		return "";
	}
}
