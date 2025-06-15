package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Nameplate;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.PageInfo;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Weapon;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XRankLeaderBoard {
	public XRankingSeasonData data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingSeasonData {
		public XRankingSeason node;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingSeason {
		public String __typename;
		public XRankingBoard xRankingAr;
		public XRankingBoard xRankingLf;
		public XRankingBoard xRankingGl;
		public XRankingBoard xRankingCl;

		public String id;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingBoard {
		public XRankingEdge[] edges;
		public PageInfo pageInfo;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingEdge {
		public String cursor;
		public XRankingNode node;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingNode {
		public String __typename;
		public String __isPlayer;

		public String id;
		public String name;
		public String nameId;
		public String byname;

		public Integer rank;
		public String rankDiff; // null, "UP", "DOWN"

		@JsonProperty("xPower")
		public Double xPower;

		public Boolean weaponTop;

		public Weapon weapon;
		public Nameplate nameplate;
	}
}
