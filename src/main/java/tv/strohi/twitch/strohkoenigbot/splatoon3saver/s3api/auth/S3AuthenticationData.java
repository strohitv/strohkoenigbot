package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3AuthenticationData {
	private String gToken;
	private String bulletToken;
}
