package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class VsHistoryDetail implements Serializable {
	private String __typename;

	private String id;
	private Id nextHistoryDetail;
	private Id previousHistoryDetail;

	private String playedTime;
	private Integer duration;

	private Player player;
	private List<Award> awards;
	private Team myTeam;
	private List<Team> otherTeams;

	private String knockout;
	private String judgement;
	private FestMatch festMatch;
	private Match bankaraMatch;

	private VsStage vsStage;
	private VsMode vsMode;
	private VsRule vsRule;

	private LeagueMatchDetails leagueMatch;

	@JsonProperty("xMatch")
	private Match xMatch;

	@JsonIgnore
	public Instant getPlayedTimeAsInstant() {
		return Instant.parse(playedTime);
	}
}
