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

	private Nothing weaponHistory;
	@JsonProperty("xMatchSeasonHistory")
	private Nothing xMatchSeasonHistory;

	private WeaponHistories weaponHistories;
	private SeasonHistories seasonHistories;

	private MatchPlayHistory bankaraMatchOpenPlayHistory;
	private MatchPlayHistory leagueMatchPlayHistory;

	private List<Id> badges;
	private List<Badge> recentBadges;
	private List<Badge> allBadges;

	private Integer weaponPowerMeasureNum;
	private Double maxWeaponPowerTotal;
	private Integer maxBestNineRank;
	private String maxBestNineRankSeasonName;
	private Double maxBestNinePower;
	private String maxBestNinePowerSeasonName;

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

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponHistories {
		public List<WeaponHistoriesEdge> edges;
		public PageInfo pageInfo;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponHistoriesEdge {
		public WeaponHistoriesNode node;
		public String cursor;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponHistoriesNode {
		public String __typename;
		public String seasonName;
		public String startTime;
		public String endTime;
		public Boolean isMonthly;
		public List<WeaponHistoriesWeaponCategories> weaponCategories;
		public List<WeaponHistoriesWeapons> weapons;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponHistoriesWeaponCategories {
		public WeaponCategory weaponCategory;
		public Double utilRatio;
		public List<WeaponHistoriesWeaponCategoriesWeapon> weapons;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponHistoriesWeaponCategoriesWeapon {
		public Weapon weapon;
		public Double utilRatio;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WeaponHistoriesWeapons {
		public Weapon weapon;
		public Double utilRatio;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SeasonHistories {
		public List<SeasonHistoriesEdge> edges;
		public PageInfo pageInfo;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SeasonHistoriesEdge {
		public SeasonHistoriesNode node;
		public String cursor;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SeasonHistoriesNode {
		public String __typename;
		public String id;
		public String seasonName;
		public Nothing bestNineHistory;
		@JsonProperty("xMatchHistory")
		public XMatchHistory xMatchHistory;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XMatchHistory {
		@JsonProperty("xRankingSeasonId")
		public String xRankingSeasonId;
		public Double powerAr;
		public Integer rankAr;
		public Double powerLf;
		public Integer rankLf;
		public Double powerGl;
		public Integer rankGl;
		public Double powerCl;
		public Integer rankCl;
	}
}
