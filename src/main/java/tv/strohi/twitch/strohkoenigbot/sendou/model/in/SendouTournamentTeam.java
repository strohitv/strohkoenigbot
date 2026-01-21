package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
public class SendouTournamentTeam implements Serializable {
	private Long id;
	private String name;
	private String url;
	private String teamPageUrl;
	private Integer seed;
	private String registeredAt;
	private Boolean checkedIn;
	private SendouTournamentTeamSeedingPower seedingPower;
	private List<SendouTournamentTeamPlayer> members;
	private String logoUrl;
	private Object mapPool;

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentTeamSeedingPower implements Serializable {
		private Double ranked;
		private Double unranked;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentTeamPlayer implements Serializable {
		private Long userId;
		private String name;
		private String battlefy;
		private String discordId;
		private String avatarUrl;
		private String country;
		private boolean captain;
		private String inGameName;
		private Pronouns pronouns;
		private String friendCode;
		private String joinedAt;
	}

	@Getter
	@Setter
	@ToString
	public static class Pronouns {
		private String subject;
		private String object;
	}
}
