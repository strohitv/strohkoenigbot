package tv.strohi.twitch.strohkoenigbot.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import tv.strohi.twitch.strohkoenigbot.chatbot.TwitchBotClient;

@RestController
@RequestMapping("/twitch-api")
@RequiredArgsConstructor
public class TwitchController {
	private final TwitchBotClient twitchBotClient;

	@GetMapping()
	public String getCode(@RequestParam("state") String state,
						  @RequestParam(value = "code", required = false) String code,
						  @RequestParam(value = "error", required = false) String error,
						  @RequestParam(value = "error_description", required = false) String errorDescription) {
		if (error != null && !error.isBlank()) {
			return String.format("%s<br><br>\n\n%s", error, errorDescription);
		}

		var result = twitchBotClient.connectAccount(state, code);

		if (result) {
			return "connection successful";
		}
		else {
			return "connection FAILED, result was false!";
		}
	}

	@GetMapping("auth")
	public String getRedirect() {
		var redirectLink = twitchBotClient.getAuthCodeGrantFlowUrl(null);

		return String.format("<html><body><a href='%s'>Twitch Auth</a></body></html>", redirectLink);
	}
}
