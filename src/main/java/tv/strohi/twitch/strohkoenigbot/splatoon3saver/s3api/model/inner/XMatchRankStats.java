package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// @Accessors(fluent = true)
public class XMatchRankStats {
	private Double power;
	private Integer rank;
	private String rankUpdateSeasonName;
	private String powerUpdateTime;
}
