package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
public class SendouTournamentMatch implements Serializable {
	private String url;
	private String bracketName;
	private String roundName;
	private SendouTournamentMatchTeam teamOne;
	private SendouTournamentMatchTeam teamTwo;
	private List<MapResult> mapList;

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentMatchTeam implements Serializable {
		private Long id;
		private Integer score;
	}
}
