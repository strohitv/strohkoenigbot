package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity(name = "splatoon_3_player")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Splatoon3Player {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	// aekptjbgz7xovgwzkomm
	private String apiId;

	// u-aekptjbgz7xovgwzkomm
	private String apiPrefixedId;
}
