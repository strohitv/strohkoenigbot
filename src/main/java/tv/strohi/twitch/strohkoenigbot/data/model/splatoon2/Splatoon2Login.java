package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "splatoon_2_login")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Splatoon2Login {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String cookie;

	private Instant expiresAt;
}
