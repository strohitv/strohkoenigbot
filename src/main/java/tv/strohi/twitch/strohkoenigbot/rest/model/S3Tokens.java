package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class S3Tokens {
	private String gToken;
	private String bulletToken;
}
