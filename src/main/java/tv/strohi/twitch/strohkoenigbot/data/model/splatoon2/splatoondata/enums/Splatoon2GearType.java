package tv.strohi.twitch.strohkoenigbot.data.model.splatoon2.splatoondata.enums;

public enum Splatoon2GearType {
	Head,
	Clothes,
	Shoes;

	public static Splatoon2GearType getGearTypeByKey(String key) {
		Splatoon2GearType type;

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
