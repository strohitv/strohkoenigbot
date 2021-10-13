package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatoonGear {
	private int rarity;
	private String id;
	private String kind;
	private String image;
	private String name;
	private String thumbnail;

	private SplatoonGearBrand brand;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatoonGearBrand {
		private String id;
		private String name;
		private String image;

		private SplatoonFrequentSkill frequent_skill;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatoonFrequentSkill {
			private String id;
			private String name;
			private String image;
		}
	}
}
