package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class ConfigFile {
	private String acc_loc;
	private String api_key;
	private String bullettoken;
	private String f_gen;
	private String gtoken;
	private String session_token;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class DownloadedGameList {
		private Map<String, StoredGame> regular_games;
		private Map<String, StoredGame> anarchy_games;
		private Map<String, StoredGame> private_games;
		private Map<String, StoredGame> salmon_games;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class StoredGame {
		private int number;
		private String filename;
		private Instant startDate;
	}
}
