package tv.strohi.twitch.strohkoenigbot.chatbot.model;

import lombok.*;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
public class TwitchClientSentAds {
	private String channelId;
	private Instant lastAdComesUpWarningSentAt;
	private Instant lastAdIsActiveWarningSentAt;
}
