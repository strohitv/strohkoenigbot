package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder(toBuilder = true)
public class SendouMatchSearchResult {
	private String type;
	private String matchUrl;

	private String tournamentName;
	private String tournamentImageUrl;

	private String bracketName;
	private String roundName;
	private String winCondition;

	private String ownTeamName;
	private String ownTeamImageUrl;
	private Integer ownScore;
	private Integer ownTeamSeed;

	private String opponentTeamName;
	private String opponentTeamImageUrl;
	private Integer opponentScore;
	private Integer opponentTeamSeed;
}
