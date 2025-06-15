package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoryResult implements Serializable {
	private HistoryResult.Data data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	// @Accessors(fluent = true)
	public static class Data implements Serializable {
		private CurrentPlayer currentPlayer;
		private PlayHistory playHistory;
	}
}
