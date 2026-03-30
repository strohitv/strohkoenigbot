package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner.VsHistoryDetail;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplayResult implements Serializable {
	private ReplayResult.Data data;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Data implements Serializable {
		private Replays replays;
		private RotationSchedulesResult.Fest currentFest;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Replays implements Serializable {
		private List<Node> nodes;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Node implements Serializable {
		private String id;
		private VsHistoryDetail historyDetail;
		private String replayCode;
	}
}
