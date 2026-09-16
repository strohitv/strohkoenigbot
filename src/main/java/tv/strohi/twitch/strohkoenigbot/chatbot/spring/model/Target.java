package tv.strohi.twitch.strohkoenigbot.chatbot.spring.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
public class Target {
	public static Target adminTarget() {
		return Target.builder().discordTarget(DiscordTarget.ADMIN).build();
	}

	public static Target channel(String channel) {
		return Target.builder()
			.discordTarget(DiscordTarget.SERVER_CHANNEL)
			.name(channel)
			.build();
	}

	private DiscordTarget discordTarget;

	private Long id;
	private String name;
}
