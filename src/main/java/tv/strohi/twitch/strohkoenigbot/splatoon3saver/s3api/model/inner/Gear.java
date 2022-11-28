package tv.strohi.twitch.strohkoenigbot.splatoon3saver.s3api.model.inner;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
// @Accessors(fluent = true)
public class Gear implements Serializable {
	private String __isGear;

	private String name;
	private Brand brand;

	private GearPowerGearPage primaryGearPower;
	private List<GearPowerGearPage> additionalGearPowers;

	private Image image;
	private Image originalImage;
	private Image thumbnailImage;

	// gear list
	private String __typename;
	private Integer rarity;

	private Long headGearId;
	private Long clothingGearId;
	private Long shoesGearId;

	private Stats stats;
}
