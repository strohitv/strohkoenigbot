package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeagueMatchDetails {
	private String teamId;
	private IdAndNameAndDescription leagueMatchEvent;
	private Double myLeaguePower;
}
