package tv.strohi.twitch.strohkoenigbot.splatoonapi.utils.model;

import lombok.*;

import java.io.IOException;

@Getter
@Setter
public class CookieRefreshException extends IOException {
	private long AccountId;

	public CookieRefreshException(long accountId, String message) {
		super(message);
	}
}
