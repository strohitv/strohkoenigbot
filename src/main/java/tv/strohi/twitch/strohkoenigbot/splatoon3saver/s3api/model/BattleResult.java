package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.CoopHistoryDetail;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.VsHistoryDetail;

import java.io.Serializable;

@Getter
@Setter
// @Accessors(fluent = true)
public class BattleResult implements Serializable {
	private Data data;

	@JsonIgnore
	private String jsonSave;

	@Getter
	@Setter
	// @Accessors(fluent = true)
	public static class Data implements Serializable {
		private VsHistoryDetail vsHistoryDetail;
		private CoopHistoryDetail coopHistoryDetail;
	}
}
