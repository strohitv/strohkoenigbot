package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class TwitchLiveEvent {
	private final boolean live;
}
