package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player;

import lombok.*;
import tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.vs.Splatoon3VsInksightPlayerStats;

import javax.persistence.*;
import java.util.List;

@Entity(name = "splatoon_3_player")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(exclude = "inksightStats")
public class Splatoon3Player {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	// aekptjbgz7xovgwzkomm
	private String apiId;

	// u-aekptjbgz7xovgwzkomm
	private String apiPrefixedId;

	private boolean isMyself;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "player")
	@EqualsAndHashCode.Exclude
	private List<Splatoon3VsInksightPlayerStats> inksightStats;
}
