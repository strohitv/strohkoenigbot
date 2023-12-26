package tv.strohi.twitch.strohkoenigbot.splatoon3saver.repo.model.vs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "splatoon_3_vs_event_regulation")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3VsEventRegulation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	private String apiId;

	private String apiLeagueMatchEventId;

	private String name;

	private String description;

	private String regulation;

	private String shortenedJson;
}
