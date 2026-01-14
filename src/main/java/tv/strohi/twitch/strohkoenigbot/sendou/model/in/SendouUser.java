package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@ToString
public class SendouUser {
	private Long id;
	private String discordId;

	private String name;
	private String country;
	private Pronouns pronouns;

	private String url;
	private String avatarUrl;

	private Integer plusServerTier;
	private SendouUserSeasonRank currentRank;
	private Double peakXp;

	private Map<String, String> socials;

	private List<SendouUserWeapon> weaponPool;
	private List<SendouUserTeamMembership> teams;
	private List<SendouUserOwnedBadge> badges;

	@Getter
	@Setter
	@ToString
	public static class SendouUserWeapon {
		private Long id;
		private String name;
		private Boolean isFiveStar;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouUserTeamMembership {
		private Long id;
		private String role;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouUserSeasonRank {
		private SendouRankTier tier;
		private Integer season;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouRankTier {
		private String name;
		private Boolean isPlus;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouUserOwnedBadge {
		private String name;
		private Integer number;
		private String imageUrl;
		private String gifUrl;
	}

	@Getter
	@Setter
	@ToString
	public static class Pronouns {
		private String subject;
		private String object;
	}


	// 	id: number;
	//	name: string;
	//	discordId: string;
	//	url: string;
	//	avatarUrl: string | null;
	//	country: string | null;
	//	socials: {
	//		twitch: string | null;
	//		// @deprecated
	//		twitter: null;
	//		battlefy: string | null;
	//		bsky: string | null;
	//	};
	//	plusServerTier: 1 | 2 | 3 | null;
	//	peakXp: number | null;
	//	weaponPool: Array<ProfileWeapon>;
	//	teams: Array<GlobalTeamMembership>;
	//	currentRank: SeasonalRank | null;
	//	pronouns: Pronouns | null;
}
