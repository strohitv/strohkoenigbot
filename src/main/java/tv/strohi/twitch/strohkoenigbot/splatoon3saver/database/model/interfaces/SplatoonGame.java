package tv.strohi.twitch.strohkoenigbot.splatoon3saver.database.model.interfaces;

import java.time.Instant;

public interface SplatoonGame {
	Instant getPlayedTime();
	Integer getDuration();
}
