package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BattleResults {
	private BattleResultsData data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleResultsData {
		private BattleHistories latestBattleHistories;
		private BattleHistories regularBattleHistories;
		private BattleHistories bankaraBattleHistories;
		private BattleHistories xBattleHistories;
		private BattleHistories privateBattleHistories;
		private BattleHistories coopResult;
		private Object currentFest;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BattleHistories {
		private Object summary;
		private Object historyGroupsOnlyFirst;
		private HistoryGroups historyGroups;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroups {
		private HistoryGroupsNodes[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupsNodes {
		private HistoryGroupsNodesDetails historyDetails;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupsNodesDetails {
		private HistoryGroupMatch[] nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SingleMatchResult {
		private SingleMatchResultData data;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SingleMatchResultData {
		private HistoryGroupMatch vsHistoryDetail;
		private HistoryGroupMatch coopHistoryDetail;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class HistoryGroupMatch {
		private String id;
		private String playedTime;
	}
}
