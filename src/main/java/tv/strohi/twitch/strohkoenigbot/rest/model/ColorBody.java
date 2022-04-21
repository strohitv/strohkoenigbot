package tv.strohi.twitch.strohkoenigbot.rest.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ColorBody {
	private int[] ownTeamColor;
	private int[] otherTeamColor;
}
