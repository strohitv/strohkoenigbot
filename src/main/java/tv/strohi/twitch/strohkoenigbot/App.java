package tv.strohi.twitch.strohkoenigbot;

import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;
import tv.strohi.twitch.strohkoenigbot.utils.LevenshteinDistanceCalculator;
import tv.strohi.twitch.strohkoenigbot.utils.MessageEscaper;

public class App {

	public static void main(String[] args) {
		new App().run();
	}

	private void run() {
		new TwitchChatBot().initialize();

		String badText = "Das hier ist äöüßÄÖÜẞ ein Ｊ໐ᏳԌЕℜ";

		System.out.println(badText);
		System.out.println(new MessageEscaper().normalize(badText));

		System.out.println(LevenshteinDistanceCalculator.calculate(badText.toLowerCase(), "das hier ist äöüßäöü ein jogger"));
		System.out.println(LevenshteinDistanceCalculator.calculate(new MessageEscaper().normalize(badText).getNormalized(), "das hier ist äöüßäöü ein jogger"));
		System.out.println(LevenshteinDistanceCalculator.calculate("eine komplett unzusammenhängende Nachricht".toLowerCase(), "das hier ist äöüßäöü ein jogger"));

		System.out.println("");
	}
}
