package tv.strohi.twitch.strohkoenigbot.data.model.splatoondata.enums;

public enum SplatoonGearType {
	Head,
	Clothes,
	Shoes;

	public static SplatoonGearType getGearTypeByKey(String key) {
		SplatoonGearType type;

		switch (key){
			case "head":
				type = Head;
				break;
			case "shoes":
				type = Shoes;
				break;
			case "clothes":
			default:
				type = Clothes;
				break;
		}

		return type;
	}
}
