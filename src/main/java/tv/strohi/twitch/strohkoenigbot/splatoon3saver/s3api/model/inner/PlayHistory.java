package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// @Accessors(fluent = true)
public class PlayHistory implements Serializable {
	private String currentTime;
	private String gameStartTime;
	private Integer rank;
	private String udemae;
	private String udemaeMax;

	@JsonProperty("xMatchRankAr")
	private Integer xMatchRankAr;
	@JsonProperty("xMatchRankCl")
	private Integer xMatchRankCl;
	@JsonProperty("xMatchRankGl")
	private Integer xMatchRankGl;
	@JsonProperty("xMatchRankLf")
	private Integer xMatchRankLf;

	@JsonProperty("xMatchMaxAr")
	private XMatchRankStats xMatchMaxAr;
	@JsonProperty("xMatchMaxCl")
	private XMatchRankStats xMatchMaxCl;
	@JsonProperty("xMatchMaxGl")
	private XMatchRankStats xMatchMaxGl;
	@JsonProperty("xMatchMaxLf")
	private XMatchRankStats xMatchMaxLf;

	private Integer winCountTotal;
	private Integer paintPointTotal;

	private List<Weapon> frequentlyUsedWeapons;

	private Object weaponHistory;
	@JsonProperty("xMatchSeasonHistory")
	private Object xMatchSeasonHistory;

	private MatchPlayHistory bankaraMatchOpenPlayHistory;
	private MatchPlayHistory leagueMatchPlayHistory;

	private List<Id> badges;
	private List<Badge> recentBadges;
	private List<Badge> allBadges;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
//	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class MatchPlayHistory {
		private Integer attend;
		private Integer bronze;
		private Integer silver;
		private Integer gold;
	}
}
