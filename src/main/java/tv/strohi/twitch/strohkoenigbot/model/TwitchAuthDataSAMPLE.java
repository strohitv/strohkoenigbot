package tv.strohi.twitch.strohkoenigbot.model;

/**
 * This is a sample class to manage Twitch Auth data with.
 * To let the application compile and use it with your own accounts, rename this class to TwitchAuthData. Make sure to fill the four fields first.
 */
public class TwitchAuthDataSAMPLE {
	private String botAuthToken = "";
	private String mainAccountAuthToken = "";
	private String mainAccountChannelId = "";
	private String mainAccountUsername = "";

	public String getBotAuthToken() {
		return botAuthToken;
	}

	public void setBotAuthToken(String botAuthToken) {
		this.botAuthToken = botAuthToken;
	}

	public String getMainAccountAuthToken() {
		return mainAccountAuthToken;
	}

	public void setMainAccountAuthToken(String mainAccountAuthToken) {
		this.mainAccountAuthToken = mainAccountAuthToken;
	}

	public String getMainAccountChannelId() {
		return mainAccountChannelId;
	}

	public void setMainAccountChannelId(String mainAccountChannelId) {
		this.mainAccountChannelId = mainAccountChannelId;
	}

	public String getMainAccountUsername() {
		return mainAccountUsername;
	}

	public void setMainAccountUsername(String mainAccountUsername) {
		this.mainAccountUsername = mainAccountUsername;
	}
}
