package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoopGroupingSchedule {
	private Image bannerImage;
	private CoopSchedule regularSchedules;
	private CoopSchedule bigRunSchedules;

	@Getter
	@Setter
	public static class CoopSchedule {
		private CoopRotation[] nodes;
	}
}
