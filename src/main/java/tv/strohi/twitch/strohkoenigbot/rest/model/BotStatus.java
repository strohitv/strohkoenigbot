package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotStatus {
	private boolean isRunning;
}
