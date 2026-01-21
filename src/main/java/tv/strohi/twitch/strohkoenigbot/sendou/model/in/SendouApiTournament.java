package tv.strohi.twitch.strohkoenigbot.sendou.model.in;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@ToString
public class SendouApiTournament implements Serializable {
	private String name;
	private String startTime;
	private String url;
	private String logoUrl;

	private SendouTournamentTeamStats teams;
	private List<SendouTournamentBracketInformation>  brackets;

	private Long organizationId;
	@JsonProperty("isFinalized")
	private Boolean isFinalized;

	public Instant getStartTimeAsInstant() {
		return Instant.parse(startTime);
	}

	public boolean hasStarted() {
		return Instant.now().isAfter(getStartTimeAsInstant());
	}

	public boolean isRunning() {
		return Instant.now().isAfter(getStartTimeAsInstant()) && !isFinalized;
	}
}
