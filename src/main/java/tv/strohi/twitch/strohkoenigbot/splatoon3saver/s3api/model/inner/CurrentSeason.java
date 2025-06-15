package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.XRankStats;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentSeason {
	public String id;
	public String name;
	public String startTime;
	public String endTime;
	public String lastUpdateTime;
	public boolean isCurrent;

	public XRankStats.XRankingAr xRankingAr;
	public XRankStats.XRankingCl xRankingCl;
	public XRankStats.XRankingGl xRankingGl;
	public XRankStats.XRankingLf xRankingLf;

	public RankingNodes ranking;

	public Instant getStartTimeAsInstant() {
		return Instant.parse(startTime);
	}

	public Instant getEndTimeAsInstant() {
		return Instant.parse(startTime);
	}

	public Instant getLastUpdateTimeAsInstant() {
		return Instant.parse(startTime);
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class RankingNodes implements Serializable {
		private Id[] nodes;
	}
}
