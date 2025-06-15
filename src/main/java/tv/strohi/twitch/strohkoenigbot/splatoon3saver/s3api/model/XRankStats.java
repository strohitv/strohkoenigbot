package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XRankStats {
	public XRankData data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankData {
		public XRanking xRanking;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRanking {
		public CurrentSeason currentSeason;
		public XRankPlayer player;
		public String region;
		public PastSeasons pastSeasons;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankPlayer{
		public String name;
		public Image userIcon;
		public XStats statsAr;
		public XStats statsCl;
		public XStats statsGl;
		public XStats statsLf;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Node {
		public String id;
		public String name;
		public Weapon weapon;
		public double xPower;
		public int rank;
		public boolean weaponTop;
		public String __isPlayer;
		public String byname;
		public String nameId;
		public Nameplate nameplate;
		public Instant startTime;
		public Instant endTime;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PastSeasons {
		public List<PastSeasonEdge> edges;
		public PageInfo pageInfo;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PastSeasonEdge {
		public PastSeasonNode node;
		public String cursor;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PastSeasonNode {
		public String id;
		public String name;
		public String __typename;
		public Instant startTime;
		public Instant endTime;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XStats {
		public Weapon weapon;
		public double lastXPower;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingAr {
		public List<Node> nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingCl {
		public List<Node> nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingGl {
		public List<Node> nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class XRankingLf {
		public List<Node> nodes;
	}
}
