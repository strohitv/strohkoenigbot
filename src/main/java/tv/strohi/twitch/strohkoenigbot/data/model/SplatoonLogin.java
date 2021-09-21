package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "splatoonLogin")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonLogin {
	@Id
	@Column(name = "id")
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "nickname")
	private String nickname;

	@Column(name = "cookie")
	private String cookie;

	@Column(name = "expiresAt")
	private Instant expiresAt;

	@Column(name = "sessionToken")
	private String sessionToken;
}
