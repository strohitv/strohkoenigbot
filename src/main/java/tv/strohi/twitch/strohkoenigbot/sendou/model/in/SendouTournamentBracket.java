package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@ToString
public class SendouTournamentBracket implements Serializable {
	private SendouTournamentBracketData data;
	private List<SendouTournamentBracketTeam> Teams;
	private Object meta;

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentBracketData implements Serializable {
		private List<Object> stage;
		private List<Object> group;
		private List<SendouTournamentBracketDataRound> round;
		private List<SendouTournamentBracketDataMatch> match;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentBracketDataRound implements Serializable {
		private Long id;
		private Long group_id;
		private Long number;
		private Long stage_id;
		private SendouTournamentBracketDataMatchMap maps;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentBracketDataMatchMap implements Serializable {
		private Integer count;
		private String type;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentBracketDataMatch implements Serializable {
		private Long id;
		private Long group_id;
		private Long number;
		private SendouTournamentBracketDataMatchParticipant opponent1;
		private SendouTournamentBracketDataMatchParticipant opponent2;
		private Long round_id;
		private Long stage_id;
		private SendouTournamentMatchStatus status;
		private Long startedAt;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentBracketDataMatchParticipant implements Serializable {
		private Long id;
		private Integer position;
		private Integer score;
		private String result;
		private Integer totalPoints;
	}

	@Getter
	@Setter
	@ToString
	public static class SendouTournamentBracketTeam implements Serializable {
		private Long id;
		private Boolean checkedIn;
	}
}
