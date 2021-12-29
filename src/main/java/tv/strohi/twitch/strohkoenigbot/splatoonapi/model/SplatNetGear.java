package tv.strohi.twitch.strohkoenigbot.splatoonapi.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplatNetGear {
	private int rarity;
	private String id;
	private String kind;
	private String image;
	private String name;
	private String thumbnail;

	private SplatNetGearBrand brand;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SplatNetGearBrand {
		private String id;
		private String name;
		private String image;

		private SplatNetFrequentSkill frequent_skill;

		@Data
		@NoArgsConstructor
		@AllArgsConstructor
		public static class SplatNetFrequentSkill {
			private String id;
			private String name;
			private String image;
		}
	}
}
