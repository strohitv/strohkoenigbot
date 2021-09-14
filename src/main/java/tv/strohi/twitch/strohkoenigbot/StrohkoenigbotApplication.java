package tv.strohi.twitch.strohkoenigbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchChatBot;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.AuthLinkCreator;
import tv.strohi.twitch.strohkoenigbot.splatoonapi.Authenticator;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class StrohkoenigbotApplication {
	private TwitchChatBot chatBot;

	@Autowired
	public void setChatBot(TwitchChatBot chatBot) {
		this.chatBot = chatBot;

		this.chatBot.initialize();
	}

	public static void main(String[] args) {
		String authUrl = new AuthLinkCreator().buildAuthUrl().toString();
		System.out.println(authUrl);

		String redirectLink = "";
		URI link = URI.create(redirectLink);

		Map<String, String> map = getQueryMap(link.getFragment());
		String sessionTokenCode = map.get("session_token_code");
		String sessionTokenCodeVerifier = map.get("state");

		String sessionToken = new Authenticator().getSessionToken("71b963c1b7b6d119", sessionTokenCode, sessionTokenCodeVerifier);

		SpringApplication.run(StrohkoenigbotApplication.class, args);
	}

	public static Map<String, String> getQueryMap(String query) {
		String[] params = query.split("&");
		Map<String, String> map = new HashMap<String, String>();

		for (String param : params) {
			String name = param.split("=")[0];
			String value = param.split("=")[1];
			map.put(name, value);
		}
		return map;
	}
}
