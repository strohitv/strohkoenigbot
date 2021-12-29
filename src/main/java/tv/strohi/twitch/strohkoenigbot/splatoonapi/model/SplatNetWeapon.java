package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetWeapon {
	private String id;
	private String name;
	private String image;
	private String thumbnail;

	private SplatNetWeaponDetail sub;
	private SplatNetWeaponDetail special;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetWeaponDetail {
		private String id;
		private String name;
		private String image_a;
		private String image_b;
	}
}
