package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class Team implements Serializable {
	private List<Player> players;
	private Integer order;
	private Result result;
	private String judgement;
	private Color color;
	private String tricolorRole;
	private String festTeamName;

	private Double festUniformBonusRate;
	private Integer festStreakWinCount;
	private String festUniformName;
}
