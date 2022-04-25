package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SplatNet2StatInkConfig {
	private String api_key;
	private String cookie;
	private String session_token;
	private String user_lang;
}
