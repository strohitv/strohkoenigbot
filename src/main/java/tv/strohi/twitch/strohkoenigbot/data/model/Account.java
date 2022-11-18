package tv.strohi.twitch.strohkoenigbot.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "account")
@Cacheable(false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private Long discordId;

	private String twitchUserId;

	private String splatoonCookie;

	private Instant splatoonCookieExpiresAt;

	private String splatoonNickname;

	private String splatoonSessionToken;

	private Boolean isMainAccount = false;

	private String timezone;

	private Boolean shouldSendDailyStats;

	private Integer rateLimitNumber;

	private String nicknameSplatoon3;

	private String gTokenSplatoon3;

	private String bulletTokenSplatoon3;

	private Integer rateLimitNumberSplatoon3;
}
