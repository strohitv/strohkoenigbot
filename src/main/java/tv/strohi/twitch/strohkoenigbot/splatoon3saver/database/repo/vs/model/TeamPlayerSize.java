package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.repo.vs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsResultTeam;

import javax.persistence.Cacheable;

@Cacheable(false)
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TeamPlayerSize {
	private Splatoon3VsResultTeam team;
	private int playerSize;

	public TeamPlayerSize(Splatoon3VsResultTeam team, long playerSize) {
		this.team = team;
		this.playerSize = Long.valueOf(playerSize).intValue();
	}
}
