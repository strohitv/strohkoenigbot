package tv.strohi.twitch.strohkoenigbot.chatbot.actions.model;

public enum GearType {
	Any("any"),
	Head("head"),
	Shirt("clothes"),
	Shoes("shoes");

	private final String name;

	GearType(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
