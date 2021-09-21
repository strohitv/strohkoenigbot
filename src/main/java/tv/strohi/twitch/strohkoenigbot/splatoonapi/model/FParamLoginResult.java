package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FParamLoginResult {
	private FParamResult result;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FParamResult {
		private String f;
		private String p1;
		private String p2;
		private String p3;
	}
}
