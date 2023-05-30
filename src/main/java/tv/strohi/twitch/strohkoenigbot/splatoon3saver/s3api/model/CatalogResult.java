package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.GearPower;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.IdAndNameAndImage;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.Image;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CatalogResult {
	private CatalogData data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class CatalogData {
		private CatalogContent catalog;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class CatalogContent {
		private String seasonName;
		private String seasonEndTime;

		private ProgressStatistics progress;
		private BonusProperties bonus;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class ProgressStatistics {
		private String __typename;
		private Integer level;
		private Long totalPoint;
		private Integer levelUpPoint;
		private Integer currentPoint;
		private ExtraReward extraReward;
		private Reward[] rewards;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class ExtraReward {
		private String __typename;
		private IdAndNameAndImage item;
		private Integer nextAcceptLevel;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Reward {
		private Integer level;
		private Integer currentPoint;
		private String state;
		private RewardItem item;

		public boolean isAchieved() {
			return state != null && state.equals("ACCEPTED");
		}

		public boolean isEmote() {
			return item != null && item.kind != null && item.kind.equals("EMOTE");
		}
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class RewardItem {
		private Image image;
		private String kind;
		private String name;
		private Integer amount;
		private GearPower primaryGearPower;
		private String id;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class BonusProperties {
		private Long dailyWinPoint;
		private Boolean isBigRun;
		private Boolean isFest;
		private Boolean isSeasonClosing;
	}
}
