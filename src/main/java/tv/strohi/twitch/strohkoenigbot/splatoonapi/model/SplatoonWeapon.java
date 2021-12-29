package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonWeapon {
	private String id;
	private String name;
	private String image;
	private String thumbnail;

	private SplatoonWeaponDetail sub;
	private SplatoonWeaponDetail special;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonWeaponDetail {
		private String id;
		private String name;
		private String image_a;
		private String image_b;
	}
}
